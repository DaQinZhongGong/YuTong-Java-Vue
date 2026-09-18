package com.yutong.ai.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.tool.domain.AiMcpMarket;
import com.yutong.ai.tool.domain.AiMcpMarketTool;
import com.yutong.ai.tool.domain.AiMcpServer;
import com.yutong.ai.tool.mapper.AiMcpMarketMapper;
import com.yutong.ai.tool.mapper.AiMcpMarketToolMapper;
import com.yutong.ai.tool.mapper.AiMcpServerMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * MCP 市场服务。设计来源: V043 P0 平价能力 — market list/get/install/sync
 * <p>约束: tenantId 透传；install 幂等（install_count 自增）；sync 占位不破坏编译。
 */
@Service
public class McpMarketService {

    private static final Logger log = LoggerFactory.getLogger(McpMarketService.class);
    public static final String RESOURCE_CODE = "ai:mcp";

    private final AiMcpMarketMapper marketMapper;
    private final AiMcpMarketToolMapper toolMapper;
    private final AiMcpServerMapper serverMapper;
    private final McpClientRegistry mcpClientRegistry;
    private final DataScopeResolver dataScopeResolver;
    private final ObjectMapper objectMapper;

    public McpMarketService(AiMcpMarketMapper marketMapper,
                            AiMcpMarketToolMapper toolMapper,
                            AiMcpServerMapper serverMapper,
                            McpClientRegistry mcpClientRegistry,
                            DataScopeResolver dataScopeResolver,
                            ObjectMapper objectMapper) {
        this.marketMapper = marketMapper;
        this.toolMapper = toolMapper;
        this.serverMapper = serverMapper;
        this.mcpClientRegistry = mcpClientRegistry;
        this.dataScopeResolver = dataScopeResolver;
        this.objectMapper = objectMapper;
    }

    public PageResult<AiMcpMarket> list(PageRequest request, String keyword, String category, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiMcpMarket> wrapper = new LambdaQueryWrapper<AiMcpMarket>()
                .eq(AiMcpMarket::getTenantId, CurrentUserContext.getTenantId())
                .like(keyword != null && !keyword.isBlank(), AiMcpMarket::getName, keyword)
                .eq(category != null && !category.isBlank(), AiMcpMarket::getCategory, category)
                .eq(status != null && !status.isBlank(), AiMcpMarket::getStatus, status)
                .orderByDesc(AiMcpMarket::getCreatedTime);
        applyDataScope(wrapper, scope);
        // keyword 同时匹配 code
        if (keyword != null && !keyword.isBlank()) {
            wrapper.or(w -> w.eq(AiMcpMarket::getTenantId, CurrentUserContext.getTenantId())
                    .like(AiMcpMarket::getCode, keyword));
        }
        Page<AiMcpMarket> page = marketMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public AiMcpMarket get(String id) {
        AiMcpMarket market = marketMapper.selectById(id);
        if (market == null) {
            throw new ResourceNotFoundException("MCP 市场条目不存在: " + id);
        }
        assertTenant(market);
        return market;
    }

    public List<AiMcpMarketTool> listTools(String marketId) {
        AiMcpMarket market = get(marketId);
        return toolMapper.selectList(new LambdaQueryWrapper<AiMcpMarketTool>()
                .eq(AiMcpMarketTool::getTenantId, CurrentUserContext.getTenantId())
                .eq(AiMcpMarketTool::getMarketId, market.getId())
                .orderByAsc(AiMcpMarketTool::getToolName));
    }

    /**
     * 安装市场条目：按 config_json 注册/复用 {@link AiMcpServer}，install_count +1。
     * 已安装同一 code 时幂等复用，不重复插入。
     */
    @Transactional
    public AiMcpMarket install(String id) {
        AiMcpMarket market = get(id);
        if (!AiMcpMarket.STATUS_PUBLISHED.equals(market.getStatus())
                && !AiMcpMarket.STATUS_DRAFT.equals(market.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 PUBLISHED/DRAFT 状态可安装，当前状态=" + market.getStatus());
        }
        upsertInstalledServer(market);
        int current = market.getInstallCount() == null ? 0 : market.getInstallCount();
        market.setInstallCount(current + 1);
        market.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = marketMapper.updateById(market);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK, "数据已被他人修改，请刷新后重试");
        }
        log.info("mcp market installed: id={} code={} installCount={}", id, market.getCode(), market.getInstallCount());
        return market;
    }

    /**
     * 同步内置 MCP 目录到当前租户（按 code 幂等 upsert），不覆盖已有自定义配置。
     */
    @Transactional
    public void sync() {
        String tenantId = CurrentUserContext.getTenantId();
        int created = 0;
        for (CatalogEntry entry : builtinCatalog()) {
            AiMcpMarket existing = marketMapper.selectOne(new LambdaQueryWrapper<AiMcpMarket>()
                    .eq(AiMcpMarket::getTenantId, tenantId)
                    .eq(AiMcpMarket::getCode, entry.code)
                    .last("LIMIT 1"));
            if (existing != null) {
                continue;
            }
            AiMcpMarket market = new AiMcpMarket();
            market.setId(IdGenerator.nextId());
            market.setTenantId(tenantId);
            market.setCreatedBy(CurrentUserContext.getUserId());
            market.setName(entry.name);
            market.setCode(entry.code);
            market.setDescription(entry.description);
            market.setProvider(entry.provider);
            market.setCategory(entry.category);
            market.setStatus(AiMcpMarket.STATUS_PUBLISHED);
            market.setInstallCount(0);
            market.setRating(java.math.BigDecimal.ZERO);
            market.setConfigJson(entry.configJson);
            market.setVersion(0);
            marketMapper.insert(market);
            for (String[] tool : entry.tools) {
                AiMcpMarketTool t = new AiMcpMarketTool();
                t.setId(IdGenerator.nextId());
                t.setTenantId(tenantId);
                t.setCreatedBy(CurrentUserContext.getUserId());
                t.setMarketId(market.getId());
                t.setToolName(tool[0]);
                t.setToolDesc(tool[1]);
                t.setInputSchema(tool[2]);
                toolMapper.insert(t);
            }
            created++;
        }
        log.info("mcp market sync completed: tenantId={} created={}", tenantId, created);
    }

    private void upsertInstalledServer(AiMcpMarket market) {
        String tenantId = CurrentUserContext.getTenantId();
        AiMcpServer existing = serverMapper.selectOne(new LambdaQueryWrapper<AiMcpServer>()
                .eq(AiMcpServer::getTenantId, tenantId)
                .eq(AiMcpServer::getServerCode, market.getCode())
                .last("LIMIT 1"));
        if (existing != null) {
            return;
        }
        JsonNode cfg = readConfig(market.getConfigJson());
        String transport = text(cfg, "transport", AiMcpServer.TRANSPORT_HTTP).toLowerCase();
        if (!List.of(AiMcpServer.TRANSPORT_SSE, AiMcpServer.TRANSPORT_STDIO, AiMcpServer.TRANSPORT_HTTP).contains(transport)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "config_json.transport 仅支持 sse/stdio/http");
        }
        String endpoint = firstText(cfg, "endpoint", "url");
        if ((AiMcpServer.TRANSPORT_SSE.equals(transport) || AiMcpServer.TRANSPORT_HTTP.equals(transport))
                && (endpoint == null || endpoint.isBlank())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "sse/http 安装需要 config_json.endpoint 或 url");
        }
        AiMcpServer server = new AiMcpServer();
        server.setServerCode(market.getCode());
        server.setServerName(market.getName());
        server.setTransport(transport);
        server.setEndpoint(endpoint);
        server.setConfigJson(market.getConfigJson());
        server.setEnabled(true);
        server.setStatus(AiMcpServer.STATUS_UNKNOWN);
        server.setRemark("installed from market " + market.getCode());
        mcpClientRegistry.saveServer(server);
    }

    private JsonNode readConfig(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "config_json 不是合法 JSON");
        }
    }

    private static String text(JsonNode cfg, String key, String fallback) {
        if (cfg == null || cfg.isMissingNode()) {
            return fallback;
        }
        JsonNode n = cfg.get(key);
        if (n == null || n.isNull() || !n.isValueNode()) {
            return fallback;
        }
        String v = n.asText();
        return v == null || v.isBlank() ? fallback : v;
    }

    private static String firstText(JsonNode cfg, String... keys) {
        for (String key : keys) {
            String v = text(cfg, key, null);
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private record CatalogEntry(String code, String name, String description, String provider,
                                String category, String configJson, String[][] tools) {}

    private static List<CatalogEntry> builtinCatalog() {
        return List.of(
                new CatalogEntry("filesystem", "Filesystem", "受控目录读写（stdio MCP）", "modelcontextprotocol", "工具",
                        "{\"transport\":\"stdio\",\"command\":\"npx\",\"args\":[\"-y\",\"@modelcontextprotocol/server-filesystem\",\"/data\"]}",
                        new String[][]{
                                {"read_file", "读取受控目录文件", "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}},\"required\":[\"path\"]}"},
                                {"write_file", "写入受控目录文件", "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"},\"content\":{\"type\":\"string\"}},\"required\":[\"path\",\"content\"]}"}
                        }),
                new CatalogEntry("fetch", "Fetch", "HTTP 抓取（stdio MCP）", "modelcontextprotocol", "工具",
                        "{\"transport\":\"stdio\",\"command\":\"npx\",\"args\":[\"-y\",\"@modelcontextprotocol/server-fetch\"]}",
                        new String[][]{
                                {"fetch", "抓取 URL 文本", "{\"type\":\"object\",\"properties\":{\"url\":{\"type\":\"string\"}},\"required\":[\"url\"]}"}
                        }),
                new CatalogEntry("postgres", "PostgreSQL", "只读 SQL 查询，需配置 endpoint", "community", "数据源",
                        "{\"transport\":\"http\",\"endpoint\":\"http://127.0.0.1:3101/mcp\"}",
                        new String[][]{
                                {"query", "执行只读 SQL", "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}},\"required\":[\"sql\"]}"}
                        }),
                new CatalogEntry("github", "GitHub", "Issue/PR 查询（stdio MCP）", "github", "协作",
                        "{\"transport\":\"stdio\",\"command\":\"npx\",\"args\":[\"-y\",\"@modelcontextprotocol/server-github\"]}",
                        new String[][]{
                                {"list_issues", "列出 Issue", "{\"type\":\"object\",\"properties\":{\"repo\":{\"type\":\"string\"}},\"required\":[\"repo\"]}"}
                        })
        );
    }

    private void assertTenant(AiMcpMarket market) {
        String currentTenant = CurrentUserContext.getTenantId();
        if (currentTenant != null && !currentTenant.equals(market.getTenantId())) {
            throw new ResourceNotFoundException("MCP 市场条目不存在: " + market.getId());
        }
    }

    private void applyDataScope(LambdaQueryWrapper<AiMcpMarket> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(AiMcpMarket::getCreatedBy, userId);
    }

    // ---- 内部创建辅助（供种子/测试，可选） ----
    @Transactional
    public AiMcpMarket createDraft(AiMcpMarket market) {
        if (market.getCode() == null || market.getCode().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "code 不能为空");
        }
        if (market.getName() == null || market.getName().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "name 不能为空");
        }
        if (market.getId() == null || market.getId().isBlank()) {
            market.setId(IdGenerator.nextId());
        }
        market.setTenantId(CurrentUserContext.getTenantId());
        market.setCreatedBy(CurrentUserContext.getUserId());
        if (market.getStatus() == null) market.setStatus(AiMcpMarket.STATUS_DRAFT);
        if (market.getInstallCount() == null) market.setInstallCount(0);
        if (market.getRating() == null) market.setRating(java.math.BigDecimal.ZERO);
        market.setVersion(0);
        try {
            marketMapper.insert(market);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "市场编码已存在: " + market.getCode());
        }
        return market;
    }
}
