package com.yutong.ai.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiToolCallLog;
import com.yutong.ai.gateway.mapper.AiToolCallLogMapper;
import com.yutong.ai.tool.domain.AiMcpServer;
import com.yutong.ai.tool.mapper.AiMcpServerMapper;
import com.yutong.ai.tool.mcp.McpCallResult;
import com.yutong.ai.tool.mcp.McpHttpClient;
import com.yutong.ai.tool.mcp.McpStdioClient;
import com.yutong.ai.tool.mcp.McpToolDescriptor;
import com.yutong.ai.tool.mcp.McpTransportClient;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 客户端注册表 — P0-4 真连接版。
 * 设计来源: V038 ai_mcp_server / Phase 3 Tool/MCP
 * 职责:
 * - CRUD + 分页查询 (带 DataScope / 租户隔离)
 * - enable/disable (乐观锁 version)
 * - 真实传输: sse/http via McpHttpClient (JDK HttpClient + JSON-RPC), stdio via McpStdioClient (ProcessBuilder)
 * - healthCheck: 真实 list_tools 调用，失败标记 UNHEALTHY（含 latency 审计）
 * - listTools / callTool: 真实 JSON-RPC，内存缓存 + 审计 AiToolCallLog（脱敏、latency、traceId）
 */
@Service
public class McpClientRegistry {

    private static final Logger log = LoggerFactory.getLogger(McpClientRegistry.class);

    public static final String RESOURCE_CODE = "ai:mcp";
    private static final long TOOLS_CACHE_TTL_MS = 60_000L;
    private static final int DEFAULT_TIMEOUT_MS = 6000;
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "password", "passwd", "secret", "token", "api_key", "apikey", "authorization", "auth", "credential", "access_token"
    );

    private final AiMcpServerMapper mcpMapper;
    private final DataScopeResolver dataScopeResolver;
    private final ObjectMapper objectMapper;
    private final AiToolCallLogMapper toolCallLogMapper;

    private final ConcurrentHashMap<String, CachedTools> toolsCache = new ConcurrentHashMap<>();

    private record CachedTools(List<McpToolDescriptor> tools, long cachedAt) {}

    public McpClientRegistry(AiMcpServerMapper mcpMapper,
                             DataScopeResolver dataScopeResolver,
                             ObjectMapper objectMapper,
                             AiToolCallLogMapper toolCallLogMapper) {
        this.mcpMapper = mcpMapper;
        this.dataScopeResolver = dataScopeResolver;
        this.objectMapper = objectMapper;
        this.toolCallLogMapper = toolCallLogMapper;
    }

    // ===== CRUD (保持原有) =====

    public PageResult<AiMcpServer> pageServers(PageRequest request, String serverCode, String transport, Boolean enabled) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiMcpServer> wrapper = new LambdaQueryWrapper<AiMcpServer>()
                .eq(AiMcpServer::getTenantId, CurrentUserContext.getTenantId())
                .like(serverCode != null && !serverCode.isBlank(), AiMcpServer::getServerCode, serverCode)
                .eq(transport != null && !transport.isBlank(), AiMcpServer::getTransport, transport)
                .eq(enabled != null, AiMcpServer::getEnabled, enabled)
                .orderByDesc(AiMcpServer::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<AiMcpServer> page = mcpMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public List<AiMcpServer> listEnabled() {
        return mcpMapper.selectList(new LambdaQueryWrapper<AiMcpServer>()
                .eq(AiMcpServer::getTenantId, CurrentUserContext.getTenantId())
                .eq(AiMcpServer::getEnabled, true)
                .orderByAsc(AiMcpServer::getServerCode));
    }

    public AiMcpServer getServer(String id) {
        AiMcpServer server = mcpMapper.selectById(id);
        if (server == null) {
            throw new ResourceNotFoundException("MCP 服务不存在: " + id);
        }
        assertTenant(server);
        return server;
    }

    @Transactional
    public AiMcpServer saveServer(AiMcpServer server) {
        if (server.getServerCode() == null || server.getServerCode().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "serverCode 不能为空");
        }
        if (server.getServerName() == null || server.getServerName().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "serverName 不能为空");
        }
        if (server.getTransport() == null || server.getTransport().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "transport 不能为空");
        }
        if (!List.of(AiMcpServer.TRANSPORT_SSE, AiMcpServer.TRANSPORT_STDIO, AiMcpServer.TRANSPORT_HTTP).contains(server.getTransport())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "transport 仅支持 sse/stdio/http");
        }
        if ((AiMcpServer.TRANSPORT_SSE.equals(server.getTransport()) || AiMcpServer.TRANSPORT_HTTP.equals(server.getTransport()))
                && (server.getEndpoint() == null || server.getEndpoint().isBlank())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "sse/http 传输类型 endpoint 不能为空");
        }
        if (server.getConfigJson() != null && server.getConfigJson().isBlank()) {
            server.setConfigJson(null);
        }

        if (server.getId() == null || server.getId().isBlank()) {
            server.setId(IdGenerator.nextId());
            server.setTenantId(CurrentUserContext.getTenantId());
            server.setCreatedBy(CurrentUserContext.getUserId());
            if (server.getEnabled() == null) server.setEnabled(true);
            if (server.getStatus() == null) server.setStatus(AiMcpServer.STATUS_UNKNOWN);
            server.setVersion(0);
            try {
                mcpMapper.insert(server);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "MCP 服务编码已存在: " + server.getServerCode());
            }
            log.info("mcp server created: id={} code={} transport={}", server.getId(), server.getServerCode(), server.getTransport());
            return server;
        }
        AiMcpServer existing = getServer(server.getId());
        checkVersion(server.getVersion(), existing.getVersion());
        server.setTenantId(existing.getTenantId());
        server.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = mcpMapper.updateById(server);
        if (rows == 0) throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        toolsCache.remove(server.getId());
        return server;
    }

    @Transactional
    public AiMcpServer enable(String id, Integer version) {
        AiMcpServer server = getServer(id);
        checkVersion(version, server.getVersion());
        server.setEnabled(true);
        server.setStatus(AiMcpServer.STATUS_UNKNOWN);
        server.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = mcpMapper.updateById(server);
        if (rows == 0) throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        log.info("mcp server enabled: id={} code={}", id, server.getServerCode());
        return server;
    }

    @Transactional
    public AiMcpServer disable(String id, Integer version) {
        AiMcpServer server = getServer(id);
        checkVersion(version, server.getVersion());
        server.setEnabled(false);
        server.setStatus(AiMcpServer.STATUS_DISABLED);
        server.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = mcpMapper.updateById(server);
        if (rows == 0) throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        toolsCache.remove(id);
        log.info("mcp server disabled: id={} code={}", id, server.getServerCode());
        return server;
    }

    // ===== P0-4 真连接 =====

    /**
     * 建立真实连接（按 transport 创建客户端并尝试 list_tools）。
     * 供启动时或手动触发，成功缓存 tools，失败抛 BusinessException。
     */
    public List<McpToolDescriptor> connect(AiMcpServer server) {
        assertTenant(server);
        if (Boolean.FALSE.equals(server.getEnabled())) {
            throw new BusinessException(ErrorCode.AI_SERVICE_DEGRADED, "MCP 服务已禁用: " + server.getServerCode());
        }
        McpTransportClient client = buildClient(server);
        try {
            List<McpToolDescriptor> tools = client.listTools();
            toolsCache.put(server.getId(), new CachedTools(List.copyOf(tools), System.currentTimeMillis()));
            log.info("mcp connect success: id={} code={} transport={} tools={}", server.getId(), server.getServerCode(), server.getTransport(), tools.size());
            return tools;
        } catch (Exception e) {
            log.warn("mcp connect failed: id={} code={} err={}", server.getId(), server.getServerCode(), e.toString());
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "MCP 连接失败: " + e.getMessage());
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 真实健康检查：调用 list_tools，成功 HEALTHY 失败 UNHEALTHY，更新 DB status。
     */
    public AiMcpServer healthCheck(String id) {
        AiMcpServer server = getServer(id);
        if (Boolean.FALSE.equals(server.getEnabled())) {
            server.setStatus(AiMcpServer.STATUS_DISABLED);
            server.setUpdatedBy(CurrentUserContext.getUserId());
            mcpMapper.updateById(server);
            return server;
        }
        long start = System.currentTimeMillis();
        String nextStatus;
        String errorMsg = null;
        try {
            // 优先尝试真实连接
            List<McpToolDescriptor> tools = doListTools(server, true);
            nextStatus = AiMcpServer.STATUS_HEALTHY;
            log.info("mcp healthCheck ok: id={} code={} tools={} latency={}ms", id, server.getServerCode(), tools.size(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            nextStatus = AiMcpServer.STATUS_UNHEALTHY;
            errorMsg = e.getMessage();
            log.warn("mcp healthCheck failed: id={} code={} err={} latency={}ms", id, server.getServerCode(), e.toString(), System.currentTimeMillis() - start);
        }
        server.setStatus(nextStatus);
        server.setUpdatedBy(CurrentUserContext.getUserId());
        mcpMapper.updateById(server);
        // 审计 healthCheck（可选，记录为 toolCall）
        if (errorMsg != null) {
            auditToolCall(server.getServerCode() + ":healthCheck", "{}", errorMsg, System.currentTimeMillis() - start, false, "HEALTH_CHECK_FAILED");
        }
        return server;
    }

    /**
     * 列出工具：真实调用 MCP list_tools，带内存缓存（TTL 60s）。
     */
    public List<McpToolDescriptor> listTools(String serverId) {
        AiMcpServer server = getServer(serverId);
        if (Boolean.FALSE.equals(server.getEnabled())) {
            throw new BusinessException(ErrorCode.AI_SERVICE_DEGRADED, "MCP 服务已禁用");
        }
        CachedTools cached = toolsCache.get(serverId);
        if (cached != null && System.currentTimeMillis() - cached.cachedAt() < TOOLS_CACHE_TTL_MS) {
            return cached.tools();
        }
        List<McpToolDescriptor> tools = doListTools(server, false);
        toolsCache.put(serverId, new CachedTools(List.copyOf(tools), System.currentTimeMillis()));
        return tools;
    }

    private List<McpToolDescriptor> doListTools(AiMcpServer server, boolean forceRefresh) {
        if (!forceRefresh) {
            CachedTools cached = toolsCache.get(server.getId());
            if (cached != null && System.currentTimeMillis() - cached.cachedAt() < TOOLS_CACHE_TTL_MS) return cached.tools();
        }
        McpTransportClient client = buildClient(server);
        try {
            return client.listTools();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "MCP list_tools 失败: " + e.getMessage());
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 调用工具：真实 JSON-RPC tool/call，记录 AiToolCallLog（含 latency、入参出参加敏、traceId、租户隔离）。
     * @return 工具返回的文本内容
     */
    public String callTool(String serverId, String toolName, String argsJson) {
        if (toolName == null || toolName.isBlank()) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "toolName 不能为空");
        AiMcpServer server = getServer(serverId);
        if (Boolean.FALSE.equals(server.getEnabled())) throw new BusinessException(ErrorCode.AI_SERVICE_DEGRADED, "MCP 服务已禁用");
        long start = System.nanoTime();
        McpTransportClient client = buildClient(server);
        McpCallResult result;
        boolean success = true;
        String errorCode = null;
        try {
            result = client.callTool(toolName, argsJson);
            if (result.isError()) {
                success = false;
                errorCode = "MCP_TOOL_ERROR";
            }
        } catch (Exception e) {
            success = false;
            errorCode = "MCP_CALL_FAILED";
            result = new McpCallResult(true, e.getMessage() != null ? e.getMessage() : e.toString(), null);
            log.warn("mcp callTool failed: server={} tool={} err={}", server.getServerCode(), toolName, e.toString());
        } finally {
            try { client.close(); } catch (Exception ignored) {}
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;
        // 审计（含脱敏）
        auditToolCall(server.getServerCode() + ":" + toolName, argsJson, result.getContent(), latencyMs, success, errorCode);
        if (!success) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "MCP 工具调用失败: " + result.getContent());
        }
        return result.getContent();
    }

    /**
     * 供 ReAct 引擎使用的便捷调用：serverId 可为 id 或 serverCode。
     */
    public String callToolByCodeOrId(String serverCodeOrId, String toolName, String argsJson) {
        AiMcpServer server = null;
        try { server = getServer(serverCodeOrId); } catch (Exception ignored) {}
        if (server == null) {
            // 按 server_code 查找
            String tenantId = CurrentUserContext.getTenantId();
            LambdaQueryWrapper<AiMcpServer> w = new LambdaQueryWrapper<AiMcpServer>()
                    .eq(AiMcpServer::getTenantId, tenantId)
                    .eq(AiMcpServer::getServerCode, serverCodeOrId)
                    .last("limit 1");
            server = mcpMapper.selectOne(w);
            if (server == null) throw new ResourceNotFoundException("MCP 服务不存在: " + serverCodeOrId);
        }
        return callTool(server.getId(), toolName, argsJson);
    }

    // ===== 内部：client 构建 / 审计 / 辅助 =====

    private McpTransportClient buildClient(AiMcpServer server) {
        String transport = server.getTransport();
        int timeoutMs = extractTimeoutMs(server.getConfigJson(), DEFAULT_TIMEOUT_MS);
        if (AiMcpServer.TRANSPORT_STDIO.equals(transport)) {
            String cfg = server.getConfigJson();
            if (cfg == null || cfg.isBlank()) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "stdio 需配置 configJson.command");
            return McpStdioClient.fromConfigJson(cfg, objectMapper, timeoutMs);
        }
        // sse / http 共用 McpHttpClient，差异仅 endpoint 约定
        String endpoint = server.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "endpoint 不能为空");
        Map<String, String> headers = extractHeaders(server.getConfigJson());
        return new McpHttpClient(endpoint, headers, timeoutMs, objectMapper);
    }

    private Map<String, String> extractHeaders(String configJson) {
        if (configJson == null || configJson.isBlank()) return Map.of();
        try {
            JsonNode root = objectMapper.readTree(configJson);
            JsonNode h = root.get("headers");
            if (h != null && h.isObject()) {
                Map<String, String> map = new HashMap<>();
                h.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
                return map;
            }
        } catch (Exception e) {
            log.debug("mcp headers parse failed: {}", e.toString());
        }
        return Map.of();
    }

    private int extractTimeoutMs(String configJson, int fallback) {
        if (configJson == null || configJson.isBlank()) return fallback;
        try {
            JsonNode root = objectMapper.readTree(configJson);
            if (root.has("timeoutMs") && root.get("timeoutMs").isNumber()) return root.get("timeoutMs").asInt();
            if (root.has("timeout_ms") && root.get("timeout_ms").isNumber()) return root.get("timeout_ms").asInt();
        } catch (Exception ignored) {}
        return fallback;
    }

    private void auditToolCall(String toolName, String inputJson, String output, long latencyMs, boolean success, String errorCode) {
        try {
            AiToolCallLog logEntity = new AiToolCallLog();
            logEntity.setId(IdGenerator.nextId());
            logEntity.setTenantId(CurrentUserContext.getTenantId());
            logEntity.setCreatedBy(CurrentUserContext.getUserId());
            logEntity.setToolName(truncate(toolName, 200));
            logEntity.setToolVersion("mcp");
            logEntity.setRiskLevel(AiToolCallLog.RISK_LOW);
            logEntity.setUserId(CurrentUserContext.getUserId());
            logEntity.setInputSummary(maskAndTruncate(inputJson, 2000));
            logEntity.setOutputSummary(maskAndTruncate(output, 2000));
            logEntity.setDataScopeSummary(CurrentUserContext.getTenantId());
            logEntity.setResult(success ? AiToolCallLog.RESULT_SUCCESS : AiToolCallLog.RESULT_FAILED);
            logEntity.setErrorCode(errorCode);
            logEntity.setLatencyMs((int) Math.min(latencyMs, Integer.MAX_VALUE));
            logEntity.setTraceId(TraceContext.getTraceId());
            toolCallLogMapper.insert(logEntity);
        } catch (Exception e) {
            log.warn("mcp audit insert failed: {}", e.toString());
        }
    }

    private String maskAndTruncate(String json, int max) {
        if (json == null) return null;
        String masked = maskSensitive(json);
        return truncate(masked, max);
    }

    private String maskSensitive(String json) {
        if (json == null) return null;
        String lower = json.toLowerCase();
        boolean hasSensitive = SENSITIVE_KEYS.stream().anyMatch(lower::contains);
        if (!hasSensitive) return json;
        try {
            JsonNode node = objectMapper.readTree(json);
            maskNode(node);
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            // 非 JSON 场景做正则脱敏
            String out = json;
            for (String k : SENSITIVE_KEYS) {
                out = out.replaceAll("(?i)([\"']?" + k + "[\"']?\\s*[:=]\\s*[\"']?)[^\"',}\\s]+", "$1***");
            }
            return out;
        }
    }

    private void maskNode(JsonNode node) {
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                String key = entry.getKey().toLowerCase();
                JsonNode val = entry.getValue();
                if (SENSITIVE_KEYS.contains(key) && val.isTextual()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) node).put(entry.getKey(), "***");
                } else {
                    maskNode(val);
                }
            });
        } else if (node.isArray()) {
            node.forEach(this::maskNode);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private void assertTenant(AiMcpServer server) {
        String ctxTenant = CurrentUserContext.getTenantId();
        if (ctxTenant != null && server.getTenantId() != null && !ctxTenant.equals(server.getTenantId())) {
            throw new BusinessException(ErrorCode.AUTH_DATA_SCOPE_DENIED, "租户无权访问该 MCP 服务");
        }
    }

    private void applyDataScope(LambdaQueryWrapper<AiMcpServer> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) { wrapper.apply("1 = 0"); return; }
        wrapper.eq(AiMcpServer::getCreatedBy, userId);
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException("版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
