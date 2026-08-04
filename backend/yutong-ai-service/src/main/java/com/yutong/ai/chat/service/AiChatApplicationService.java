package com.yutong.ai.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.gateway.domain.AiConversation;
import com.yutong.ai.gateway.domain.AiMessage;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.mapper.AiConversationMapper;
import com.yutong.ai.gateway.mapper.AiMessageMapper;
import com.yutong.ai.gateway.service.AiAuditService;
import com.yutong.ai.gateway.service.AiToolRegistry;
import com.yutong.ai.governance.service.AiCostGovernanceService;
import com.yutong.ai.rag.service.RagRetrievalService;
import com.yutong.api.facade.LicenseService;
import com.yutong.auth.AuthAdapter;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.trace.TraceContext;
import com.yutong.ai.chat.dto.AiChatRequest;
import com.yutong.ai.chat.dto.AiChatVO;
import com.yutong.ai.chat.dto.AiStreamDeltaData;
import com.yutong.ai.chat.dto.AiStreamDoneData;
import com.yutong.ai.chat.dto.AiStreamErrorData;
import com.yutong.ai.chat.dto.AiStreamEvent;
import com.yutong.ai.chat.dto.AiStreamMetaData;
import com.yutong.ai.chat.dto.ApplySuggestionRequest;
import com.yutong.ai.chat.service.llm.LlmMessage;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

/**
 * AI 对话应用服务。设计来源: 13-AI能力设计、52-后端服务分工
 * 核心约束:
 * - AI 不直接修改生产数据，所有建议只进草稿区
 * - 应用建议前必须展示差异预览，回传 schema/hash/expectedVersion/idempotencyKey
 * - 不在事务内调用 AI 或外部 HTTP
 * - 工具调用必须经过白名单校验
 * - 禁止把密钥、token、密码发送给模型
 */
@Service
public class AiChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AiChatApplicationService.class);

    /** AI 会话资源编码，对齐 permissions.yaml ai 命名空间。 */
    public static final String RESOURCE_CODE = "ai:conversation";

    /** SSE 流式回复单次 delta 的字符块大小（模拟逐字输出，对齐 13 号文档 line 140 流式语义） */
    private static final int STREAM_CHUNK_SIZE = 4;
    /** SSE 单次 delta 之间的间隔毫秒（模拟模型生成延迟，便于前端逐字渲染可见） */
    private static final long STREAM_CHUNK_INTERVAL_MS = 20L;
    /** SSE emitter 超时时间: 5 分钟，覆盖大多数模型生成场景 */
    private static final long SSE_TIMEOUT_MS = 5L * 60 * 1000;

    private final AiConversationMapper conversationMapper;
    private final AiMessageMapper messageMapper;
    private final AiToolRegistry toolRegistry;
    private final AiAuditService auditService;
    private final RagRetrievalService ragRetrievalService;
    private final com.yutong.common.metrics.PlatformMetrics platformMetrics;
    private final AuthAdapter authAdapter;
    private final ObjectMapper objectMapper;
    private final LicenseService licenseService;
    private final LlmProviderSelector providerSelector;
    private final AiCostGovernanceService aiCostGovernanceService;
    private final DataScopeResolver dataScopeResolver;

    /**
     * GA2-31: SSE 流式专用线程池。
     * 使用固定大小线程池，避免阻塞 Servlet 容器主线程；任务隔离，与同步 chat() 互不干扰。
     * 命名线程便于线程 dump 排查。
     */
    private final ExecutorService streamingExecutor =
            Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "yutong-ai-sse-stream-" + System.nanoTime());
                t.setDaemon(true);
                return t;
            });

    public AiChatApplicationService(AiConversationMapper conversationMapper,
                                    AiMessageMapper messageMapper,
                                    AiToolRegistry toolRegistry,
                                    AiAuditService auditService,
                                    RagRetrievalService ragRetrievalService,
                                    com.yutong.common.metrics.PlatformMetrics platformMetrics,
                                    AuthAdapter authAdapter,
                                    ObjectMapper objectMapper,
                                    LicenseService licenseService,
                                    LlmProviderSelector providerSelector,
                                    AiCostGovernanceService aiCostGovernanceService,
                                    DataScopeResolver dataScopeResolver) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.toolRegistry = toolRegistry;
        this.auditService = auditService;
        this.ragRetrievalService = ragRetrievalService;
        this.platformMetrics = platformMetrics;
        this.authAdapter = authAdapter;
        this.objectMapper = objectMapper;
        this.licenseService = licenseService;
        this.providerSelector = providerSelector;
        this.aiCostGovernanceService = aiCostGovernanceService;
        this.dataScopeResolver = dataScopeResolver;
    }

    @PreDestroy
    public void shutdown() {
        streamingExecutor.shutdown();
    }

    /**
     * AI 对话。设计来源: 13-AI能力设计 chatWithAssistant
     * 第一版: 不调用真实模型 API，返回模拟响应（带 RAG 引用）
     * 真实模型集成留 v0.5+
     */
    public AiChatVO chat(AiChatRequest request) {
        long startTime = System.currentTimeMillis();
        String userId = CurrentUserContext.getUserId();
        String tenantId = CurrentUserContext.getTenantId();
        String traceId = TraceContext.getTraceId();

        // 1. 创建或获取会话（不放在事务里，因为后续可能调用外部 API）
        AiConversation conversation = getOrCreateConversation(
                request.getConversationId(), request.getScenario(), userId, tenantId);

        // 2. 保存用户消息
        AiMessage userMessage = saveMessage(conversation.getId(),
                AiMessage.ROLE_USER, request.getMessage(), null, null, null, null);

        // 3. RAG 检索（如果提供了 kbId）
        List<RagRetrievalService.RetrievalResult> retrievalResults = List.of();
        if (request.getKbId() != null && !request.getKbId().isBlank()) {
            retrievalResults = ragRetrievalService.retrieve(
                    tenantId, request.getKbId(), request.getMessage(), userId, 5);
        }

        // 4. 工具调用校验（如果 scenario 涉及工具）
        // GA2-03-5: 白名单校验 + 权限码校验 (TC-SEC-AI-001)。未授权用户调用 AI 工具抛 AUTH-403001。
        String usedTool = resolveToolForScenario(request.getScenario());
        if (usedTool != null) {
            toolRegistry.validateTool(usedTool);
            AiToolRegistry.ToolMeta toolMeta = toolRegistry.getTool(usedTool);
            if (toolMeta != null && toolMeta.permissionCode() != null) {
                authAdapter.requirePermission(toolMeta.permissionCode());
            }
        }

        // 4b. GA2-L172: AI 额度校验（70 号文档「额度扣减规则」先检查再扣减）
        // 预估 token 用量 = 输入 token + 预估输出 token（按输入长度等量预估）
        int estimatedInputTokens = estimateTokens(request.getMessage());
        int estimatedOutputTokens = estimatedInputTokens; // 简化预估：输出 ≈ 输入
        licenseService.checkQuota("ai.monthly.tokens", estimatedInputTokens + estimatedOutputTokens);

        // GA2-45: AI 成本额度治理校验
        aiCostGovernanceService.checkQuotaBeforeCall(
                request.getScenario(), request.getProviderCode(), request.getModelCode(),
                estimatedInputTokens + estimatedOutputTokens);

        // 5. 构建引用列表
        List<AiChatVO.Citation> citations = retrievalResults.stream()
                .map(this::toCitation)
                .toList();

        // 6. 生成回复：优先调用真实 LLM 供应商，无可用供应商时降级为 mock
        LlmResponse llmResponse = generateReply(
                conversation.getId(), request.getMessage(), request.getScenario(),
                request.getProviderCode(), request.getModelCode(),
                retrievalResults, citations);
        String replyContent = llmResponse.content();
        String providerCode = llmResponse.providerCode();
        String modelCode = llmResponse.modelCode();
        int tokenInput = llmResponse.tokenInput();
        int tokenOutput = llmResponse.tokenOutput();
        int latencyMs = llmResponse.latencyMs();

        // 7. 保存 AI 消息
        AiMessage aiMessage = saveMessage(conversation.getId(),
                AiMessage.ROLE_ASSISTANT, replyContent, serializeCitations(citations),
                tokenInput, tokenOutput, latencyMs);

        // 7b. GA2-L172: 记录额度使用量（按实际 token 消耗扣减，多退少补）
        licenseService.recordQuotaUsage("ai.monthly.tokens", tokenInput + tokenOutput);

        // 8. 更新会话最后消息时间
        conversation.setLastMessageTime(OffsetDateTime.now());
        conversation.setModelCode(modelCode);
        conversationMapper.updateById(conversation);

        // 9. 记录工具调用审计
        if (usedTool != null) {
            AiToolRegistry.ToolMeta toolMeta = toolRegistry.getTool(usedTool);
            String riskLevel = toolMeta != null ? toolMeta.riskLevel() : "A3";
            auditService.recordToolCall(
                    usedTool, riskLevel, userId,
                    request.getMessage(), replyContent,
                    buildToolCallSummary(toolMeta, retrievalResults),
                    "SUCCESS", null, latencyMs, traceId);
        }

        // 10. 记录成本日志（GA2-45: 实际成本 + 额度用量累计）
        aiCostGovernanceService.recordCostAfterCall(
                request.getScenario(), providerCode, modelCode, conversation.getId(),
                tokenInput, tokenOutput, latencyMs, "SUCCESS");
        // 10b. P5-04 业务指标埋点 (62-可观测性详设)
        platformMetrics.recordAiRequest(tenantId, request.getScenario(), "SUCCESS",
                java.time.Duration.ofMillis(latencyMs));
        platformMetrics.recordAiTokenUsage(tenantId, providerCode, tokenInput + tokenOutput);
        if (usedTool != null) {
            platformMetrics.recordAiToolCall(tenantId, usedTool, "SUCCESS");
        }

        // 11. 构建响应
        AiChatVO vo = new AiChatVO();
        vo.setConversationId(conversation.getId());
        vo.setMessageId(aiMessage.getId());
        vo.setContent(replyContent);
        vo.setCitations(citations);
        vo.setScenario(request.getScenario());
        vo.setModelCode(modelCode);
        vo.setTokenInput(tokenInput);
        vo.setTokenOutput(tokenOutput);
        vo.setLatencyMs(latencyMs);
        vo.setCreatedTime(OffsetDateTime.now());
        return vo;
    }

    /**
     * GA2-31 SSE 流式 AI 对话。设计来源: 13-AI能力设计 line 137-143/252、contracts/openapi/openapi.yaml AiStreamEvent
     * <p>
     * 内容协商: Accept: text/event-stream 时由 Controller 调用本方法，返回 SseEmitter。
     * 事件序列: meta → (citation)* → (delta)+ → done | error
     * <p>
     * 硬约束:
     * - 每个事件携带 eventId / sequence / conversationId / messageId，sequence 从 0 递增
     * - 流式 token 不写 ai_cost_log，等到流结束后一次性写入完整 token 用量（13 号文档 line 143）
     * - 服务端异常推送 event: error，不再继续推送 delta（13 号文档 line 141）
     * - 用户可随时断开连接触发 AbortController，emitter 自动 completeWithError
     * <p>
     * 实现要点:
     * - 使用专用 streamingExecutor 线程池，避免阻塞 Servlet 主线程
     * - CurrentUserContext/TraceContext 是 ThreadLocal，需在子线程中重新设置（捕获请求线程快照）
     * - 鉴权（requirePermission）必须在请求线程完成（MockAuthAdapter 依赖 RequestContextHolder），
     *   子线程只做流式推送和持久化，不再调用 requirePermission
     * - 第一版仍是 mock-model，将 generateMockReply 完整回复按 STREAM_CHUNK_SIZE 字符分块推送 delta
     */
    public SseEmitter streamChat(AiChatRequest request) {
        // 捕获请求线程的 ThreadLocal 快照（子线程无法继承 ThreadLocal）
        final String userId = CurrentUserContext.getUserId();
        final String tenantId = CurrentUserContext.getTenantId();
        final String username = CurrentUserContext.getUsername();
        final String deptId = CurrentUserContext.getDeptId();
        final String deptPath = CurrentUserContext.getDeptPath();
        final com.yutong.common.auth.DataScopeType dataScopeType = CurrentUserContext.getDataScopeType();
        final String traceId = TraceContext.getTraceId();
        final long startTime = System.currentTimeMillis();

        // —— 请求线程阶段: 鉴权 + 会话/消息/RAG/工具校验 ——
        // MockAuthAdapter.requirePermission 依赖 RequestContextHolder，必须在请求线程完成。
        // 若鉴权失败抛 PermissionDeniedException，由 GlobalExceptionHandler 返回 403，emitter 不会返回给客户端。
        AiConversation conversation = getOrCreateConversation(
                request.getConversationId(), request.getScenario(), userId, tenantId);
        final String conversationId = conversation.getId();
        final AiConversation finalConversation = conversation;

        saveMessage(conversation.getId(),
                AiMessage.ROLE_USER, request.getMessage(), null, null, null, null);

        List<RagRetrievalService.RetrievalResult> tempResults = List.of();
        if (request.getKbId() != null && !request.getKbId().isBlank()) {
            tempResults = ragRetrievalService.retrieve(
                    tenantId, request.getKbId(), request.getMessage(), userId, 5);
        }
        final List<RagRetrievalService.RetrievalResult> retrievalResults = tempResults;

        final String usedTool = resolveToolForScenario(request.getScenario());
        final boolean requiresHumanConfirmation;
        if (usedTool != null) {
            toolRegistry.validateTool(usedTool);
            AiToolRegistry.ToolMeta toolMeta = toolRegistry.getTool(usedTool);
            if (toolMeta != null && toolMeta.permissionCode() != null) {
                authAdapter.requirePermission(toolMeta.permissionCode());
            }
            requiresHumanConfirmation = toolMeta != null && isRiskLevelAtLeastA2(toolMeta.riskLevel());
        } else {
            requiresHumanConfirmation = false;
        }

        // GA2-L172: AI 额度校验（70 号文档「额度扣减规则」AI 流式调用开始时预占额度）
        // 预估 token = 输入 token + 预估输出 token（按输入长度等量预估）
        int estimatedInputTokens = estimateTokens(request.getMessage());
        int estimatedOutputTokens = estimatedInputTokens;
        licenseService.checkQuota("ai.monthly.tokens", estimatedInputTokens + estimatedOutputTokens);

        // GA2-45: AI 成本额度治理校验
        aiCostGovernanceService.checkQuotaBeforeCall(
                request.getScenario(), request.getProviderCode(), request.getModelCode(),
                estimatedInputTokens + estimatedOutputTokens);

        final List<AiChatVO.Citation> citations = retrievalResults.stream()
                .map(this::toCitation)
                .toList();
        final LlmResponse llmResponse = generateReply(
                conversationId, request.getMessage(), request.getScenario(),
                request.getProviderCode(), request.getModelCode(),
                retrievalResults, citations);
        final String replyContent = llmResponse.content();
        final String providerCode = llmResponse.providerCode();
        final String modelCode = llmResponse.modelCode();
        final String messageId = IdGenerator.nextId();

        // 此时所有同步鉴权/校验已完成，PermissionDeniedException 等业务异常已可在创建 emitter 之前抛出，
        // 由 GlobalExceptionHandler 走同步异常处理路径返回 403 等标准 HTTP 状态码（GA2-31 修复权限拒绝返回 500 的问题）。
        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // —— 异步阶段: SSE 流式推送 + 流后持久化 ——
        streamingExecutor.execute(() -> {
            // 在子线程恢复上下文，保证 MyBatis-Plus MetaObjectHandler 和审计日志能读取到用户/租户信息
            CurrentUserContext.set(userId, tenantId, username, deptId, deptPath, dataScopeType);
            TraceContext.setTraceId(traceId);

            int sequence = 0;
            try {
                // 1. 推送 meta 事件（流开始）
                AiStreamMetaData metaData = new AiStreamMetaData(modelCode, request.getScenario());
                sendSseEvent(emitter, AiStreamEvent.TYPE_META, sequence++,
                        conversationId, messageId, metaData);

                // 2. 推送 citation 事件（每条检索结果一个事件）
                for (AiChatVO.Citation citation : citations) {
                    sendSseEvent(emitter, AiStreamEvent.TYPE_CITATION, sequence++,
                            conversationId, messageId, citation);
                }

                // 3. 分块推送 delta（模拟逐字输出）
                int replyLen = replyContent.length();
                for (int i = 0; i < replyLen; i += STREAM_CHUNK_SIZE) {
                    int end = Math.min(i + STREAM_CHUNK_SIZE, replyLen);
                    String chunk = replyContent.substring(i, end);
                    AiStreamDeltaData deltaData = new AiStreamDeltaData(chunk);
                    sendSseEvent(emitter, AiStreamEvent.TYPE_DELTA, sequence++,
                            conversationId, messageId, deltaData);
                    // 模拟模型生成延迟，便于前端逐字渲染可见
                    if (STREAM_CHUNK_INTERVAL_MS > 0) {
                        Thread.sleep(STREAM_CHUNK_INTERVAL_MS);
                    }
                }

                // 4. 统计 token 用量（流结束后一次性计算）
                int tokenInput = estimateTokens(request.getMessage());
                int tokenOutput = estimateTokens(replyContent);
                int latencyMs = (int) (System.currentTimeMillis() - startTime);

                // 5. 推送 done 事件（携带 usage 和 requiresHumanConfirmation）
                AiStreamDoneData.AiUsage usage = new AiStreamDoneData.AiUsage(
                        tokenInput, tokenOutput, latencyMs, "0", "CNY");
                AiStreamDoneData doneData = new AiStreamDoneData(usage, requiresHumanConfirmation);
                sendSseEvent(emitter, AiStreamEvent.TYPE_DONE, sequence++,
                        conversationId, messageId, doneData);

                // 6. 保存 AI 消息（流结束后一次性写入，token 用量完整）
                saveMessage(finalConversation.getId(),
                        AiMessage.ROLE_ASSISTANT, replyContent, serializeCitations(citations),
                        tokenInput, tokenOutput, latencyMs);

                // 6b. GA2-L172: 记录额度使用量（70 号文档「额度扣减规则」流结束后按实际 token 结算，多退少补）
                licenseService.recordQuotaUsage("ai.monthly.tokens", tokenInput + tokenOutput);

                // 7. 更新会话最后消息时间
                finalConversation.setLastMessageTime(OffsetDateTime.now());
                conversationMapper.updateById(finalConversation);

                // 8. 记录工具调用审计
                if (usedTool != null) {
                    AiToolRegistry.ToolMeta toolMeta = toolRegistry.getTool(usedTool);
                    String riskLevel = toolMeta != null ? toolMeta.riskLevel() : "A3";
                    auditService.recordToolCall(
                            usedTool, riskLevel, userId,
                            request.getMessage(), replyContent,
                            buildToolCallSummary(toolMeta, retrievalResults),
                            "SUCCESS", null, latencyMs, traceId);
                }

                // 9. 记录成本日志（流结束后一次性写入，对齐 13 号文档 line 143；GA2-45 实际成本 + 累计）
                aiCostGovernanceService.recordCostAfterCall(
                        request.getScenario(), providerCode, modelCode, finalConversation.getId(),
                        tokenInput, tokenOutput, latencyMs, "SUCCESS");

                // 10. 业务指标埋点
                platformMetrics.recordAiRequest(tenantId, request.getScenario(), "SUCCESS",
                        java.time.Duration.ofMillis(latencyMs));
                platformMetrics.recordAiTokenUsage(tenantId, providerCode, tokenInput + tokenOutput);
                if (usedTool != null) {
                    platformMetrics.recordAiToolCall(tenantId, usedTool, "SUCCESS");
                }

                emitter.complete();
                log.debug("SSE 流式回复完成. traceId={}, conversationId={}, messageId={}, sequence={}, latencyMs={}",
                        traceId, conversationId, messageId, sequence, latencyMs);
            } catch (Exception ex) {
                log.error("SSE 流式回复异常. traceId={}, conversationId={}, messageId={}",
                        traceId, conversationId, messageId, ex);
                int latencyMs = (int) (System.currentTimeMillis() - startTime);
                try {
                    AiStreamErrorData errorData = new AiStreamErrorData(
                            "AI-500001",
                            "ai.chat.stream.error",
                            traceId,
                            true);
                    sendSseEvent(emitter, AiStreamEvent.TYPE_ERROR, sequence++,
                            conversationId, messageId, errorData);
                } catch (Exception sendErr) {
                    log.warn("推送 SSE error 事件失败. traceId={}", traceId, sendErr);
                }
                platformMetrics.recordAiRequest(tenantId, request.getScenario(), "FAILED",
                        java.time.Duration.ofMillis(latencyMs));
                emitter.completeWithError(ex);
            } finally {
                // 清理子线程 ThreadLocal，避免线程池复用导致的上下文泄漏
                CurrentUserContext.clear();
                TraceContext.clear();
            }
        });

        // 客户端断开/超时回调（仅记录日志，不强制中断生成）
        emitter.onCompletion(() -> log.debug("SSE emitter 已完成. traceId={}", traceId));
        emitter.onTimeout(() -> {
            log.warn("SSE emitter 超时. traceId={}", traceId);
            emitter.complete();
        });
        emitter.onError(throwable -> log.warn("SSE emitter 异常. traceId={}", traceId, throwable));

        return emitter;
    }

    /** 推送一个 SSE 事件。封装事件信封构造和 SseEmitter.send 调用。 */
    private void sendSseEvent(SseEmitter emitter, String eventType, int sequence,
                              String conversationId, String messageId, Object data) throws IOException {
        AiStreamEvent event = new AiStreamEvent(
                AiStreamEvent.buildEventId(messageId, sequence),
                eventType, sequence, conversationId, messageId, data);
        emitter.send(SseEmitter.event()
                .id(event.getEventId())
                .name(eventType)
                .data(event));
    }

    /**
     * 判断风险等级是否 >= A2（A2/A3/A4 需要人工确认，A0/A1 不需要）。
     * 设计来源: 13-AI能力设计 AI 工具风险分级与 67-数据权限与审计日志详设。
     */
    private boolean isRiskLevelAtLeastA2(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return false;
        }
        return switch (riskLevel) {
            case "A2", "A3", "A4" -> true;
            default -> false;
        };
    }

    /**
     * 获取当前租户所有可用的 LLM 模型选项（供前端选择器使用）。
     * 设计来源: P6-02 免费 LLM 供应商集成
     */
    public List<LlmProviderSelector.ModelOption> listAvailableModels() {
        return providerSelector.listAvailableModels();
    }

    public PageResult<AiConversation> pageConversations(PageRequest request, String scenario) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiConversation> wrapper = new LambdaQueryWrapper<AiConversation>()
                .eq(AiConversation::getTenantId, CurrentUserContext.getTenantId())
                .eq(AiConversation::getUserId, CurrentUserContext.getUserId())
                .eq(scenario != null && !scenario.isBlank(), AiConversation::getScenario, scenario)
                .orderByDesc(AiConversation::getLastMessageTime);
        // GA2-DS: 接入 DataScope (SELF scope)，对非 admin 角色按 created_by 过滤
        applyDataScope(wrapper, scope);
        Page<AiConversation> page = conversationMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件 (SELF scope)。
     * AiConversation 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF: created_by = currentUserId (biz 用户)
     * - DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 实体无对应 dept 字段，安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<AiConversation> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        // 非 admin: 追加 SELF 过滤 (AiConversation 无 owner_user_id，使用 created_by)
        wrapper.eq(AiConversation::getCreatedBy, userId);
    }

    public AiConversation getConversation(String id) {
        AiConversation conversation = conversationMapper.selectById(id);
        if (conversation == null) {
            throw new ResourceNotFoundException("会话不存在: " + id);
        }
        return conversation;
    }

    public List<AiMessage> getConversationMessages(String conversationId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<AiMessage>()
                        .eq(AiMessage::getConversationId, conversationId)
                        .orderByAsc(AiMessage::getCreatedTime));
    }

    /**
     * 应用 AI 建议。设计来源: 13-AI能力设计 applyAiSuggestion
     * 关键约束: 只能进入草稿区，必须校验 schema/hash/version/idempotencyKey
     */
    @Transactional
    public String applySuggestion(ApplySuggestionRequest request) {
        // 校验必填
        if (request.getDraftId() == null || request.getDraftId().isBlank()) {
            throw new BusinessConflictException("draftId 不能为空");
        }
        if (request.getSchemaVersion() == null || request.getSchemaVersion().isBlank()) {
            throw new BusinessConflictException("schemaVersion 不能为空");
        }
        if (request.getConfigHash() == null || request.getConfigHash().isBlank()) {
            throw new BusinessConflictException("configHash 不能为空");
        }
        if (request.getExpectedVersion() == null) {
            throw new BusinessConflictException("expectedVersion 不能为空");
        }
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isBlank()) {
            throw new BusinessConflictException("idempotencyKey 不能为空");
        }

        // 第一版: 仅返回确认信息，实际草稿写入由低代码服务处理
        // AI 服务只做校验和审计，不直接写低代码表
        String auditNote = "draftId=" + request.getDraftId()
                + ", type=" + request.getDraftType()
                + ", schema=" + request.getSchemaVersion()
                + ", hash=" + request.getConfigHash()
                + ", version=" + request.getExpectedVersion();
        return "AI 建议已校验通过，请到低代码草稿区确认应用: " + auditNote;
    }

    private AiConversation getOrCreateConversation(String conversationId, String scenario,
                                                    String userId, String tenantId) {
        if (conversationId != null && !conversationId.isBlank()) {
            AiConversation existing = conversationMapper.selectById(conversationId);
            if (existing == null) {
                throw new ResourceNotFoundException("会话不存在: " + conversationId);
            }
            return existing;
        }
        AiConversation conversation = new AiConversation();
        conversation.setId(IdGenerator.nextId());
        conversation.setTenantId(tenantId);
        conversation.setCreatedBy(userId);
        conversation.setConversationNo("CONV" + OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%04d", ThreadLocalRandom.current().nextInt(10000)));
        conversation.setUserId(userId);
        conversation.setTitle("AI 对话 " + OffsetDateTime.now()
                .format(DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        conversation.setScenario(scenario);
        conversation.setLocale("zh-CN");
        conversation.setModelCode("mock-chat");
        conversation.setStatus(AiConversation.STATUS_ACTIVE);
        conversation.setLastMessageTime(OffsetDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    private AiMessage saveMessage(String conversationId, String role, String content,
                                  String citationJson, Integer tokenInput,
                                  Integer tokenOutput, Integer latencyMs) {
        AiMessage message = new AiMessage();
        message.setId(IdGenerator.nextId());
        message.setTenantId(CurrentUserContext.getTenantId());
        message.setCreatedBy(CurrentUserContext.getUserId());
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContentSummary(content);
        message.setContentEncrypted(content); // 第一版未加密，实际应加密存储
        message.setCitationJson(citationJson);
        message.setTokenInput(tokenInput);
        message.setTokenOutput(tokenOutput);
        message.setLatencyMs(latencyMs);
        messageMapper.insert(message);
        return message;
    }

    private String resolveToolForScenario(String scenario) {
        if (scenario == null) return null;
        return switch (scenario) {
            case "PAGE_GENERATE" -> "generate_page_draft";
            case "SQL_EXPLAIN" -> "generate_sql_draft";
            case "PLATFORM_QA" -> "query_meta_model";
            default -> null;
        };
    }

    /**
     * 生成 AI 回复：优先调用真实 LLM 供应商，失败时尝试下一个启用的非 mock 供应商，全部失败时降级为 mock。
     * 设计来源: P6-02 免费 LLM 供应商集成
     */
    private LlmResponse generateReply(String conversationId, String userMessage, String scenario,
                                      String providerCode, String modelCode,
                                      List<RagRetrievalService.RetrievalResult> retrievalResults,
                                      List<AiChatVO.Citation> citations) {
        var providerOpt = providerSelector.selectProvider(providerCode, modelCode);
        if (providerOpt.isEmpty()) {
            return mockReply(userMessage, scenario, retrievalResults, 0);
        }

        var runtime = providerOpt.get();
        LlmResponse response = callProvider(runtime, conversationId, userMessage, scenario, retrievalResults, citations);
        if (!response.isError()) {
            return response;
        }

        LlmResponse nextResponse = tryNextEnabledProvider(
                runtime.provider().getProviderCode(), conversationId, userMessage, scenario,
                retrievalResults, citations);
        if (nextResponse != null && !nextResponse.isError()) {
            return nextResponse;
        }

        log.warn("所有真实 LLM 供应商均失败，降级为 mock: lastProvider={} model={} err={}",
                runtime.provider().getProviderCode(), runtime.defaultModel(),
                response.error() != null ? response.error().getMessage() : "unknown");
        return mockReply(userMessage, scenario, retrievalResults, response.latencyMs());
    }

    /**
     * 依次尝试当前租户所有启用的非 mock 供应商（跳过指定已失败的供应商），直到成功或耗尽。
     */
    private LlmResponse tryNextEnabledProvider(String excludeProviderCode, String conversationId,
                                               String userMessage, String scenario,
                                               List<RagRetrievalService.RetrievalResult> retrievalResults,
                                               List<AiChatVO.Citation> citations) {
        List<LlmProviderSelector.ProviderRuntime> providers = providerSelector.selectEnabledProviders();
        for (LlmProviderSelector.ProviderRuntime runtime : providers) {
            String code = runtime.provider().getProviderCode();
            if (code == null || code.equals(excludeProviderCode)) {
                continue;
            }
            LlmResponse response = callProvider(runtime, conversationId, userMessage, scenario, retrievalResults, citations);
            if (!response.isError()) {
                return response;
            }
            log.warn("备选 LLM 供应商调用失败: provider={} model={} err={}",
                    code, runtime.defaultModel(),
                    response.error() != null ? response.error().getMessage() : "unknown");
        }
        return null;
    }

    private LlmResponse callProvider(LlmProviderSelector.ProviderRuntime runtime, String conversationId,
                                     String userMessage, String scenario,
                                     List<RagRetrievalService.RetrievalResult> retrievalResults,
                                     List<AiChatVO.Citation> citations) {
        List<LlmMessage> messages = buildLlmMessages(
                runtime.provider(), conversationId, userMessage, scenario, retrievalResults, citations);
        LlmRequest llmRequest = new LlmRequest(runtime.defaultModel(), messages, 0.7, null, false, scenario);
        return runtime.adapter().chat(llmRequest);
    }

    private LlmResponse mockReply(String userMessage, String scenario,
                                  List<RagRetrievalService.RetrievalResult> retrievalResults,
                                  int latencyMs) {
        String mockContent = generateMockReply(userMessage, scenario, retrievalResults);
        return new LlmResponse("mock-local", "mock-chat", mockContent,
                estimateTokens(userMessage), estimateTokens(mockContent),
                latencyMs, "stop", null);
    }

    /**
     * 构造发送给 LLM 的消息列表：system prompt + 历史消息 + 当前用户消息。
     * 包含 RAG 引用摘要，便于模型基于知识库回答。
     * <p>
     * 对 Pollinations 等匿名免 Key 供应商，仅保留单条 user 消息，将 system prompt / RAG / 场景
     * 折叠进 user content，避免触发其认证计费路径（P6-02 免费 LLM 供应商集成）。
     */
    private List<LlmMessage> buildLlmMessages(AiProvider provider, String conversationId, String userMessage,
                                              String scenario,
                                              List<RagRetrievalService.RetrievalResult> retrievalResults,
                                              List<AiChatVO.Citation> citations) {
        String systemPrompt = buildSystemPrompt(scenario, retrievalResults, citations);

        // 匿名免 Key 供应商（Pollinations 等）通常只接受单条 user 消息，且对内容长度/格式敏感。
        // 折叠 system prompt 会显著增加内容长度并触发其免费额度限制，因此仅发送原始用户消息，
        // 由 OpenAiCompatibleAdapter 再做长度截断兜底（P6-02 免费 LLM 供应商集成）。
        boolean anonymousMode = isAnonymousProvider(provider);
        if (anonymousMode) {
            return List.of(LlmMessage.user(userMessage));
        }

        List<LlmMessage> messages = new ArrayList<>();
        messages.add(LlmMessage.system(systemPrompt));

        // 加入最近 10 轮历史消息（避免 token 过多）
        List<AiMessage> history = getConversationMessages(conversationId);
        int historyStart = Math.max(0, history.size() - 10);
        for (int i = historyStart; i < history.size(); i++) {
            AiMessage msg = history.get(i);
            String role = msg.getRole();
            String content = msg.getContentEncrypted() != null ? msg.getContentEncrypted() : msg.getContentSummary();
            if (content == null) {
                content = "";
            }
            if (AiMessage.ROLE_USER.equals(role)) {
                messages.add(LlmMessage.user(content));
            } else if (AiMessage.ROLE_ASSISTANT.equals(role)) {
                messages.add(LlmMessage.assistant(content));
            }
        }
        messages.add(LlmMessage.user(userMessage));
        return messages;
    }

    /**
     * 判断当前供应商是否走匿名免 Key 路径。apiKeyRef 解析为空且明确属于匿名供应商时返回 true。
     */
    private boolean isAnonymousProvider(AiProvider provider) {
        if (provider == null || provider.getApiKeyRef() == null) {
            return false;
        }
        String apiKey = providerSelector.resolveApiKey(provider.getApiKeyRef());
        if (apiKey != null && !apiKey.isBlank()) {
            return false;
        }
        // 目前仅 Pollinations 公共端点明确支持匿名调用；ollama/mock-local 单独处理
        return "pollinations".equals(provider.getProviderCode());
    }

    private String buildSystemPrompt(String scenario,
                                     List<RagRetrievalService.RetrievalResult> retrievalResults,
                                     List<AiChatVO.Citation> citations) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 YuTong 平台的 AI 助手，基于企业知识库为用户提供专业、准确的回答。\n");
        sb.append("规则：\n");
        sb.append("1. 仅基于提供的知识库内容回答，不确定时明确说明。\n");
        sb.append("2. 禁止泄露用户密码、密钥、token 等敏感信息。\n");
        sb.append("3. 涉及修改生产数据的建议必须说明需要人工确认。\n");
        if (scenario != null && !scenario.isBlank()) {
            sb.append("当前场景: ").append(scenario).append("\n");
        }
        if (!retrievalResults.isEmpty()) {
            sb.append("\n参考内容:\n");
            for (int i = 0; i < retrievalResults.size(); i++) {
                RagRetrievalService.RetrievalResult r = retrievalResults.get(i);
                sb.append(i + 1).append(". ").append(r.docTitle())
                        .append(" - ").append(r.sectionPath())
                        .append(" (得分: ").append(String.format("%.2f", r.score())).append(")\n");
            }
        }
        return sb.toString();
    }

    private String generateMockReply(String userMessage, String scenario,
                                     List<RagRetrievalService.RetrievalResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("收到您的问题: \"").append(userMessage).append("\"\n\n");
        sb.append("场景: ").append(scenario != null ? scenario : "通用问答").append("\n\n");
        if (results.isEmpty()) {
            sb.append("当前未检索到相关文档内容。如果是知识库问题，请先入库文档。\n");
        } else {
            sb.append("已检索到 ").append(results.size()).append(" 条相关内容:\n");
            for (int i = 0; i < results.size(); i++) {
                RagRetrievalService.RetrievalResult r = results.get(i);
                sb.append(i + 1).append(". ").append(r.docTitle())
                        .append(" - ").append(r.sectionPath())
                        .append(" (得分: ").append(String.format("%.2f", r.score())).append(")\n");
            }
        }
        sb.append("\n[模拟回复] 这是 v0.4 的模拟响应，真实模型集成将在 v0.5 实现。");
        return sb.toString();
    }

    private AiChatVO.Citation toCitation(RagRetrievalService.RetrievalResult r) {
        AiChatVO.Citation c = new AiChatVO.Citation();
        c.setDocumentId(r.documentId());
        c.setChunkId(r.chunkId());
        c.setDocTitle(r.docTitle());
        c.setSectionPath(r.sectionPath());
        c.setSourceType(r.sourceType());
        c.setScore(r.score());
        return c;
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        // 简化估算: 中文按 1 字符 = 1 token，英文按 4 字符 = 1 token
        return (int) Math.ceil(text.length() / 2.0);
    }

    private String buildToolCallSummary(AiToolRegistry.ToolMeta toolMeta,
                                        List<RagRetrievalService.RetrievalResult> results) {
        if (toolMeta == null) {
            return "results=" + results.size();
        }
        return "tool=" + toolMeta.name() + ", risk=" + toolMeta.riskLevel()
                + ", results=" + results.size();
    }

    /**
     * 序列化引用列表为合法 JSON 字符串，写入 ai_message.citation_json (jsonb 列)。
     * 修复 API-ISSUE-005: 原 citations.toString() 产生非 JSON 格式，且 jsonb 列类型不匹配。
     */
    private String serializeCitations(List<AiChatVO.Citation> citations) {
        if (citations == null || citations.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (JsonProcessingException e) {
            log.warn("序列化 citations 失败，写入 null。traceId={}", TraceContext.getTraceId(), e);
            return null;
        }
    }
}
