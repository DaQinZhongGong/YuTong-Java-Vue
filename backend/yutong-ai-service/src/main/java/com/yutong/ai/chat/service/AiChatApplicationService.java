package com.yutong.ai.chat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.gateway.domain.AiConversation;
import com.yutong.ai.gateway.domain.AiMessage;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.agent.domain.AiAgent;
import com.yutong.ai.agent.service.AgentService;
import com.yutong.ai.gateway.mapper.AiConversationMapper;import com.yutong.ai.gateway.mapper.AiMessageMapper;
import com.yutong.ai.gateway.service.AiAuditService;
import com.yutong.ai.gateway.service.AiProviderRegistry;
import com.yutong.ai.gateway.service.AiToolRegistry;
import com.yutong.ai.governance.service.AiCostGovernanceService;
import com.yutong.ai.rag.service.RagRetrievalService;
import com.yutong.api.facade.LicenseService;
import com.yutong.auth.AuthAdapter;
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
import com.yutong.ai.chat.dto.AiChatRequest;
import com.yutong.ai.chat.dto.AiChatVO;
import com.yutong.ai.chat.dto.ChatAttachment;
import com.yutong.ai.chat.dto.AiStreamDeltaData;
import com.yutong.ai.chat.dto.AiStreamDoneData;
import com.yutong.ai.chat.dto.AiStreamErrorData;
import com.yutong.ai.chat.dto.AiStreamEvent;
import com.yutong.ai.chat.dto.AiStreamMetaData;
import com.yutong.ai.chat.dto.ApplySuggestionRequest;
import com.yutong.ai.chat.service.llm.LlmMessage;
import com.yutong.ai.chat.service.llm.LlmProviderAdapter;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.chat.service.llm.LlmRequest;
import com.yutong.ai.chat.service.llm.LlmResponse;
import com.yutong.ai.chat.service.llm.AiModelPriceResolver;
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
    private final AiProviderRegistry providerRegistry;
    private final AiCostGovernanceService aiCostGovernanceService;
    private final com.yutong.ai.chat.service.llm.AiModelPriceResolver aiModelPriceResolver;
    private final DataScopeResolver dataScopeResolver;
    private final com.yutong.ai.trace.service.AiTraceService aiTraceService;
    private final com.yutong.ai.memory.service.AiMemoryService aiMemoryService;
    private final AgentService agentService;

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
                                    AiProviderRegistry providerRegistry,
                                    AiCostGovernanceService aiCostGovernanceService,
                                    com.yutong.ai.chat.service.llm.AiModelPriceResolver aiModelPriceResolver,
                                    DataScopeResolver dataScopeResolver,
                                    com.yutong.ai.trace.service.AiTraceService aiTraceService,
                                    com.yutong.ai.memory.service.AiMemoryService aiMemoryService,
                                    AgentService agentService) {
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
        this.providerRegistry = providerRegistry;
        this.aiCostGovernanceService = aiCostGovernanceService;
        this.aiModelPriceResolver = aiModelPriceResolver;
        this.dataScopeResolver = dataScopeResolver;
        this.aiTraceService = aiTraceService;
        this.aiMemoryService = aiMemoryService;
        this.agentService = agentService;
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

        // 2. 保存用户消息 (V052: 挂分支父链)
        String userParentId = resolveParentMessageId(conversation.getId(), request.getParentMessageId());
        AiMessage userMessage = saveMessage(conversation.getId(),
                AiMessage.ROLE_USER, request.getMessage(), null, null, null, null, userParentId);

        // 3. RAG 检索（如果提供了 kbId）
        List<RagRetrievalService.RetrievalResult> retrievalResults = List.of();
        if (request.getKbId() != null && !request.getKbId().isBlank()) {
            retrievalResults = ragRetrievalService.retrieve(
                    tenantId, request.getKbId(), request.getMessage(), userId, 5);
        }

        // 4. 工具调用校验（如果 scenario 涉及工具）
        // GA2-03-5: 白名单校验 + 权限码校验 (TC-SEC-AI-001)。未授权用户调用 AI 工具抛 AUTH-403001。
        String traceRunId = startChatTrace(request, conversation.getId());

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

        LlmResponse llmResponse;
        try {
            llmResponse = generateReply(
                    conversation.getId(), request.getMessage(), request.getScenario(),
                    request.getProviderCode(), request.getModelCode(),
                    request.getProviderType(), request.getModelType(), request.getEndpoint(),
                    request.getAgentId(),
                    retrievalResults, citations);
        } catch (Exception ex) {
            int failedMs = (int) (System.currentTimeMillis() - startTime);
            finishChatTrace(traceRunId, false, conversation.getId(), request.getModelCode(), 0, 0, failedMs, ex.getMessage());
            throw ex;
        }
        String replyContent = llmResponse.content();
        String providerCode = llmResponse.providerCode();
        String modelCode = llmResponse.modelCode();
        int tokenInput = llmResponse.tokenInput();
        int tokenOutput = llmResponse.tokenOutput();
        int latencyMs = llmResponse.latencyMs();
        if (llmResponse.isError()) {
            String err = llmResponse.error() == null ? "llm error" : llmResponse.error().getMessage();
            finishChatTrace(traceRunId, false, conversation.getId(), modelCode, tokenInput, tokenOutput, latencyMs, err);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "LLM 调用失败: " + err);
        }

        // 7. 保存 AI 消息 (V052: 父链指向本轮用户消息)
        AiMessage aiMessage = saveMessage(conversation.getId(),
                AiMessage.ROLE_ASSISTANT, replyContent, serializeCitations(citations),
                tokenInput, tokenOutput, latencyMs, userMessage.getId());

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
        finishChatTrace(traceRunId, true, conversation.getId(), modelCode, tokenInput, tokenOutput, latencyMs, null);
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
        PreparedStream prepared = prepareStream(request);
        final SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        final String traceId = prepared.traceId;
        emitter.onCompletion(() -> log.debug("SSE emitter 已完成. traceId={}", traceId));
        emitter.onTimeout(() -> {
            log.warn("SSE emitter 超时. traceId={}", traceId);
            emitter.complete();
        });
        emitter.onError(throwable -> log.warn("SSE emitter 异常. traceId={}", traceId, throwable));
        launchPreparedStream(prepared, new SseChatStreamListener(emitter));
        return emitter;
    }

    /**
     * 流式对话（传输无关）。WebSocket / SSE 共用：鉴权与会话准备在调用线程完成，
     * 生成在 streamingExecutor 中回调 {@link ChatStreamListener}。
     */
    public void streamToListener(AiChatRequest request, ChatStreamListener listener) {
        PreparedStream prepared = prepareStream(request);
        launchPreparedStream(prepared, listener);
    }

    private PreparedStream prepareStream(AiChatRequest request) {
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

        // V052: 用户消息挂分支父链, id 透传给异步线程供 assistant 回链
        final String userParentId = resolveParentMessageId(conversation.getId(), request.getParentMessageId());
        final String reqUserMessageId = saveMessage(conversation.getId(),
                AiMessage.ROLE_USER, request.getMessage(), null, null, null, null, userParentId).getId();

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
        final String messageId = IdGenerator.nextId();
        final String reqProviderCode = request.getProviderCode();
        final String reqModelCode = request.getModelCode();
        final String reqProviderType = request.getProviderType();
        final String reqModelType = request.getModelType();
        final String reqEndpoint = request.getEndpoint();
        final String reqScenario = request.getScenario();
        final String reqMessage = request.getMessage();
        final List<String> reqImageUrls = ChatAttachment.imageUrls(request.getAttachments());
        final String reqAgentId = request.getAgentId();

        String traceRunId = startChatTrace(request, conversationId);
        return new PreparedStream(
                userId, tenantId, username, deptId, deptPath, dataScopeType, traceId, startTime,
                conversationId, finalConversation, retrievalResults, usedTool, requiresHumanConfirmation,
                citations, messageId, reqProviderCode, reqModelCode, reqProviderType, reqModelType,
                reqEndpoint, reqScenario, reqMessage, reqImageUrls, reqAgentId, reqUserMessageId, traceRunId);
    }

    private void launchPreparedStream(PreparedStream p, ChatStreamListener listener) {
        streamingExecutor.execute(() -> {
            CurrentUserContext.set(p.userId, p.tenantId, p.username, p.deptId, p.deptPath, p.dataScopeType);
            TraceContext.setTraceId(p.traceId);
            int sequence = 0;
            java.util.Optional<LlmProviderSelector.ProviderRuntime> runtimeOpt =
                    resolveRuntime(p.reqProviderType, p.reqModelType, p.reqProviderCode, p.reqModelCode, p.reqEndpoint);
            String effectiveProviderCode = runtimeOpt.map(r -> r.provider().getProviderCode()).orElse("none");
            String effectiveModelCode = runtimeOpt.map(LlmProviderSelector.ProviderRuntime::defaultModel).orElse("unknown");
            try {
                emitEvent(listener, AiStreamEvent.TYPE_META, sequence++, p.conversationId, p.messageId,
                        new AiStreamMetaData(effectiveModelCode, p.reqScenario));
                for (AiChatVO.Citation citation : p.citations) {
                    emitEvent(listener, AiStreamEvent.TYPE_CITATION, sequence++, p.conversationId, p.messageId, citation);
                }
                if (p.usedTool != null) {
                    emitEvent(listener, AiStreamEvent.TYPE_TOOL, sequence++, p.conversationId, p.messageId,
                            java.util.Map.of(
                                    "id", p.usedTool,
                                    "name", p.usedTool,
                                    "status", "success",
                                    "summary", "scenario=" + nvl(p.reqScenario)));
                }

                StringBuilder fullContent = new StringBuilder();
                java.util.concurrent.atomic.AtomicReference<LlmProviderAdapter.StreamFinish> finishRef =
                        new java.util.concurrent.atomic.AtomicReference<>(null);
                java.util.concurrent.atomic.AtomicReference<Throwable> streamErrorRef =
                        new java.util.concurrent.atomic.AtomicReference<>(null);
                java.util.concurrent.atomic.AtomicInteger seqRef = new java.util.concurrent.atomic.AtomicInteger(sequence);
                boolean realStreamAttempted = false;
                boolean realStreamSucceeded = false;

                if (runtimeOpt.isPresent()) {
                    realStreamAttempted = true;
                    LlmProviderSelector.ProviderRuntime runtime = runtimeOpt.get();
                    List<LlmMessage> messages = buildLlmMessages(
                            runtime.provider(), p.conversationId, p.reqMessage, p.reqScenario, p.retrievalResults, p.citations, p.reqImageUrls, p.reqAgentId);
                    LlmRequest llmReq = new LlmRequest(runtime.defaultModel(), messages, 0.7, null, true, p.reqScenario);
                    try {
                        runtime.adapter().stream(llmReq,
                                delta -> {
                                    if (delta == null || delta.isEmpty()) {
                                        return;
                                    }
                                    fullContent.append(delta);
                                    try {
                                        int off = 0;
                                        while (off < delta.length()) {
                                            int end = Math.min(off + STREAM_CHUNK_SIZE, delta.length());
                                            emitEvent(listener, AiStreamEvent.TYPE_DELTA,
                                                    seqRef.getAndIncrement(), p.conversationId, p.messageId,
                                                    new AiStreamDeltaData(delta.substring(off, end)));
                                            off = end;
                                        }
                                    } catch (IOException ioe) {
                                        log.warn("推送流式 delta 失败，终止流. traceId={}", p.traceId, ioe);
                                        throw new RuntimeException(ioe);
                                    }
                                },
                                finishRef::set,
                                streamErrorRef::set);
                        if (streamErrorRef.get() == null) {
                            realStreamSucceeded = true;
                        } else {
                            log.warn("LLM 流式异常，准备降级 mock: provider={} model={} err={}",
                                    effectiveProviderCode, effectiveModelCode, streamErrorRef.get().getMessage());
                        }
                    } catch (Exception e) {
                        streamErrorRef.set(e);
                        log.warn("LLM 流式调用异常，降级 mock: provider={} err={}", effectiveProviderCode, e.getMessage());
                    }
                    sequence = seqRef.get();
                }

                if (!runtimeOpt.isPresent()) {
                    throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "未配置可用 LLM 供应商");
                }
                if (!realStreamSucceeded || fullContent.length() == 0) {
                    Throwable err = streamErrorRef.get();
                    String msg = err == null ? "LLM 流式无输出" : err.getMessage();
                    throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "LLM 流式失败: " + msg);
                }
                String replyContent = fullContent.toString();
                String providerCodeForCost = effectiveProviderCode;
                String modelCodeForCost = effectiveModelCode;

                int tokenInput;
                int tokenOutput;
                LlmProviderAdapter.StreamFinish finish = finishRef.get();
                if (finish != null && finish.tokenInput() != null && finish.tokenOutput() != null
                        && (finish.tokenInput() > 0 || finish.tokenOutput() > 0)) {
                    tokenInput = finish.tokenInput();
                    tokenOutput = finish.tokenOutput();
                    if (tokenInput == 0) {
                        tokenInput = estimateTokens(p.reqMessage);
                    }
                    if (tokenOutput == 0) {
                        tokenOutput = estimateTokens(replyContent);
                    }
                } else {
                    tokenInput = estimateTokens(p.reqMessage);
                    tokenOutput = estimateTokens(replyContent);
                }
                int latencyMs = (int) (System.currentTimeMillis() - p.startTime);

                // P2 8-C: 动态计算本次调用成本 (基于 ai_provider.modelListJson 配置 + 真实 token 用量)
                String estimatedCostStr = "0";
                try {
                    String modelListJson = null;
                    String priceModelCode = null;
                    if (p.reqProviderCode != null && !p.reqProviderCode.isBlank()) {
                        var runtime = providerSelector.selectProvider(p.reqProviderCode, p.reqModelCode);
                        if (runtime.isPresent() && runtime.get().provider() != null) {
                            modelListJson = runtime.get().provider().getModelListJson();
                            priceModelCode = runtime.get().defaultModel() != null
                                    ? runtime.get().defaultModel() : p.reqModelCode;
                        }
                    }
                    if (priceModelCode == null) priceModelCode = p.reqModelCode;
                    AiModelPriceResolver.Price price = aiModelPriceResolver.resolvePrice(priceModelCode, modelListJson);
                    if (price != null && (price.inputCnyPer1k().signum() > 0 || price.outputCnyPer1k().signum() > 0)) {
                        // input/ output 单价 元/1K tokens, 转换为元 (6 位小数 µ¥)
                        java.math.BigDecimal cost = price.inputCnyPer1k()
                                .multiply(java.math.BigDecimal.valueOf(tokenInput))
                                .divide(java.math.BigDecimal.valueOf(1000), 6, java.math.RoundingMode.HALF_UP)
                                .add(price.outputCnyPer1k()
                                        .multiply(java.math.BigDecimal.valueOf(tokenOutput))
                                        .divide(java.math.BigDecimal.valueOf(1000), 6, java.math.RoundingMode.HALF_UP));
                        estimatedCostStr = cost.toPlainString();
                    }
                } catch (Exception ignored) {
                    // 成本计算失败不阻塞主流程, 仍写 "0"
                }

                AiStreamDoneData.AiUsage usage = new AiStreamDoneData.AiUsage(
                        tokenInput, tokenOutput, latencyMs, estimatedCostStr, "CNY");
                // V052 P2-C: 落库 assistant 消息并回传 messageId, 前端据此挂载反馈按钮
                AiMessage savedAssistantMessage = saveMessage(p.conversation.getId(),
                        AiMessage.ROLE_ASSISTANT, replyContent, serializeCitations(p.citations),
                        tokenInput, tokenOutput, latencyMs, p.reqUserMessageId);
                String savedAssistantMessageId = savedAssistantMessage.getId();
                emitEvent(listener, AiStreamEvent.TYPE_DONE, sequence++,
                        p.conversationId, p.messageId, new AiStreamDoneData(usage, p.requiresHumanConfirmation, savedAssistantMessageId));

                licenseService.recordQuotaUsage("ai.monthly.tokens", tokenInput + tokenOutput);
                p.conversation.setLastMessageTime(OffsetDateTime.now());
                p.conversation.setModelCode(modelCodeForCost);
                conversationMapper.updateById(p.conversation);

                if (p.usedTool != null) {
                    AiToolRegistry.ToolMeta toolMeta = toolRegistry.getTool(p.usedTool);
                    String riskLevel = toolMeta != null ? toolMeta.riskLevel() : "A3";
                    auditService.recordToolCall(
                            p.usedTool, riskLevel, p.userId,
                            p.reqMessage, replyContent,
                            buildToolCallSummary(toolMeta, p.retrievalResults),
                            "SUCCESS", null, latencyMs, p.traceId);
                }
                aiCostGovernanceService.recordCostAfterCall(
                        p.reqScenario, providerCodeForCost, modelCodeForCost, p.conversation.getId(),
                        tokenInput, tokenOutput, latencyMs, "SUCCESS");
                platformMetrics.recordAiRequest(p.tenantId, p.reqScenario, "SUCCESS",
                        java.time.Duration.ofMillis(latencyMs));
                platformMetrics.recordAiTokenUsage(p.tenantId, providerCodeForCost, tokenInput + tokenOutput);
                if (p.usedTool != null) {
                    platformMetrics.recordAiToolCall(p.tenantId, p.usedTool, "SUCCESS");
                }
                listener.onComplete();
                finishChatTrace(p.traceRunId, true, p.conversationId, modelCodeForCost, tokenInput, tokenOutput, latencyMs, null);
                log.debug("流式回复完成. traceId={}, conversationId={}, messageId={}, sequence={}, latencyMs={}, provider={}, model={}, fallback={}",
                        p.traceId, p.conversationId, p.messageId, sequence, latencyMs, providerCodeForCost, modelCodeForCost, !realStreamSucceeded);
            } catch (Exception ex) {
                log.error("流式回复异常. traceId={}, conversationId={}, messageId={}",
                        p.traceId, p.conversationId, p.messageId, ex);
                int latencyMs = (int) (System.currentTimeMillis() - p.startTime);
                try {
                    emitEvent(listener, AiStreamEvent.TYPE_ERROR, sequence++,
                            p.conversationId, p.messageId,
                            new AiStreamErrorData("AI-500001", "ai.chat.stream.error", p.traceId, true));
                } catch (Exception sendErr) {
                    log.warn("推送 error 事件失败. traceId={}", p.traceId, sendErr);
                }
                platformMetrics.recordAiRequest(p.tenantId, p.reqScenario, "FAILED",
                        java.time.Duration.ofMillis(latencyMs));
                finishChatTrace(p.traceRunId, false, p.conversationId, effectiveModelCode, 0, 0, latencyMs, ex.getMessage());
                listener.onFailure(ex);
            } finally {
                CurrentUserContext.clear();
                TraceContext.clear();
            }
        });
    }

    private void emitEvent(ChatStreamListener listener, String eventType, int sequence,
                           String conversationId, String messageId, Object data) throws IOException {
        listener.onEvent(new AiStreamEvent(
                AiStreamEvent.buildEventId(messageId, sequence),
                eventType, sequence, conversationId, messageId, data));
    }

    private static final class SseChatStreamListener implements ChatStreamListener {
        private final SseEmitter emitter;

        private SseChatStreamListener(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onEvent(AiStreamEvent event) throws IOException {
            emitter.send(SseEmitter.event()
                    .id(event.getEventId())
                    .name(event.getEventType())
                    .data(event));
        }

        @Override
        public void onComplete() {
            emitter.complete();
        }

        @Override
        public void onFailure(Throwable error) {
            emitter.completeWithError(error);
        }
    }

    private record PreparedStream(
            String userId,
            String tenantId,
            String username,
            String deptId,
            String deptPath,
            DataScopeType dataScopeType,
            String traceId,
            long startTime,
            String conversationId,
            AiConversation conversation,
            List<RagRetrievalService.RetrievalResult> retrievalResults,
            String usedTool,
            boolean requiresHumanConfirmation,
            List<AiChatVO.Citation> citations,
            String messageId,
            String reqProviderCode,
            String reqModelCode,
            String reqProviderType,
            String reqModelType,
            String reqEndpoint,
            String reqScenario,
            String reqMessage,
            List<String> reqImageUrls,
            String reqAgentId,
            String reqUserMessageId,
            String traceRunId
    ) {}

    private String startChatTrace(AiChatRequest request, String conversationId) {
        if (aiTraceService == null) {
            return null;
        }
        try {
            com.yutong.ai.trace.domain.AiTraceRun run = new com.yutong.ai.trace.domain.AiTraceRun();
            run.setTraceType(com.yutong.ai.trace.domain.AiTraceRun.TYPE_LLM);
            run.setInputJson("{\"conversationId\":\"" + nvl(conversationId)
                    + "\",\"scenario\":\"" + nvl(request.getScenario())
                    + "\",\"kbId\":\"" + nvl(request.getKbId()) + "\"}");
            return aiTraceService.startRun(run).getId();
        } catch (Exception e) {
            log.debug("start chat trace skipped: {}", e.getMessage());
            return null;
        }
    }

    private void finishChatTrace(String runId, boolean success, String conversationId, String modelCode,
                                 int tokenInput, int tokenOutput, int latencyMs, String error) {
        if (aiTraceService == null || runId == null || runId.isBlank()) {
            return;
        }
        try {
            com.yutong.ai.trace.domain.AiTraceNode node = new com.yutong.ai.trace.domain.AiTraceNode();
            node.setRunId(runId);
            node.setNodeType("llm");
            node.setStatus(success ? com.yutong.ai.trace.domain.AiTraceNode.STATUS_SUCCESS
                    : com.yutong.ai.trace.domain.AiTraceNode.STATUS_FAILED);
            node.setLatencyMs(latencyMs);
            node.setOutputJson("{\"model\":\"" + nvl(modelCode) + "\",\"tokenInput\":" + tokenInput
                    + ",\"tokenOutput\":" + tokenOutput + "}");
            if (error != null) {
                node.setErrorMessage(error);
            }
            aiTraceService.addNode(node);
            aiTraceService.finishRun(runId,
                    success ? com.yutong.ai.trace.domain.AiTraceRun.STATUS_SUCCESS
                            : com.yutong.ai.trace.domain.AiTraceRun.STATUS_FAILED,
                    "{\"conversationId\":\"" + nvl(conversationId) + "\",\"model\":\"" + nvl(modelCode) + "\"}",
                    latencyMs, error);
        } catch (Exception e) {
            log.debug("finish chat trace skipped: {}", e.getMessage());
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s.replace("\"", "");
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
                // V049 P2-C: 置顶会话排最前, 其次按最后消息时间
                .orderByDesc(AiConversation::getPinned)
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
     * 消息反馈 (P2-C 会话管理)。
     * 仅 assistant 消息可评价; feedback 为 LIKE/DISLIKE, 空值 = 清除评价。
     * 不存在 → 404; 非 assistant → 400 (失败关闭, 不静默忽略)。
     */
    public AiMessage feedbackMessage(String messageId, String feedback) {
        AiMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw new ResourceNotFoundException("消息不存在: " + messageId);
        }
        if (!AiMessage.ROLE_ASSISTANT.equals(message.getRole())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 assistant 回复可评价");
        }
        if (feedback == null || feedback.isBlank()) {
            message.setFeedback(null);
        } else {
            String normalized = feedback.trim().toUpperCase();
            if (!AiMessage.FEEDBACK_LIKE.equals(normalized) && !AiMessage.FEEDBACK_DISLIKE.equals(normalized)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "feedback 仅支持 LIKE/DISLIKE");
            }
            message.setFeedback(normalized);
        }
        messageMapper.updateById(message);
        return message;
    }

    /**
     * 会话置顶 / 取消置顶 (P2-C 会话管理)。
     */
    public AiConversation pinConversation(String id, boolean pinned) {
        AiConversation conversation = getConversation(id);
        conversation.setPinned(pinned);
        conversationMapper.updateById(conversation);
        return conversation;
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
        return saveMessage(conversationId, role, content, citationJson,
                tokenInput, tokenOutput, latencyMs, null);
    }

    private AiMessage saveMessage(String conversationId, String role, String content,
                                  String citationJson, Integer tokenInput,
                                  Integer tokenOutput, Integer latencyMs, String parentMessageId) {
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
        message.setParentMessageId(parentMessageId);
        messageMapper.insert(message);
        return message;
    }

    /**
     * 校验父消息归属 (V052 P2-C 分支链)。
     * 空 → null (链首); 不存在 → 404; 非同一会话 → 400 (防跨会话挂靠)。
     */
    String resolveParentMessageId(String conversationId, String parentMessageId) {
        if (parentMessageId == null || parentMessageId.isBlank()) {
            return null;
        }
        String pid = parentMessageId.trim();
        AiMessage parent = messageMapper.selectById(pid);
        if (parent == null) {
            throw new ResourceNotFoundException("父消息不存在: " + pid);
        }
        if (!conversationId.equals(parent.getConversationId())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "父消息不属于当前会话");
        }
        return pid;
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
     * 生成 AI 回复（兼容旧调用）：优先调用真实 LLM 供应商，失败降级 mock。
     */
    private LlmResponse generateReply(String conversationId, String userMessage, String scenario,
                                      String providerCode, String modelCode,
                                      List<RagRetrievalService.RetrievalResult> retrievalResults,
                                      List<AiChatVO.Citation> citations) {
        return generateReply(conversationId, userMessage, scenario,
                providerCode, modelCode, null, null, null, null,
                retrievalResults, citations);
    }

    /**
     * P0-2 真实 LLM 直连闭环：按 providerType 分发，优先经 AiProviderRegistry.selectPrimary(providerType, modelType)
     * 取 endpoint/apiKey/model；非流式调用 OpenAiCompatibleAdapter.chatCompletion 同步返回；失败降级 mock。
     * <p>
     * 分发规则:
     * - openai/deepseek/qianwen/zhipu/ollama/minimax/atlas/xiaomi → OPENAI_COMPATIBLE HTTP 流（经 registry）
     * - dify/coze → 协议为 CUSTOM 时尝试 OPENAI_COMPATIBLE 透传，失败降级 mock（后续可扩展专用适配器）
     * - custom_api → 透传 endpoint/model（请求中 endpoint 覆盖 DB，modelCode 透传不校验 modelListJson）
     * - providerType 为空时根据 providerCode 推断，仍走 registry；无匹配则回退 providerSelector.selectProvider
     */
    private LlmResponse generateReply(String conversationId, String userMessage, String scenario,
                                      String providerCode, String modelCode,
                                      String providerType, String modelType, String customEndpoint,
                                      String agentId,
                                      List<RagRetrievalService.RetrievalResult> retrievalResults,
                                      List<AiChatVO.Citation> citations) {
        // 1) 优先经 registry 按 providerType/modelType 分发
        var registryRuntime = resolveRuntime(providerType, modelType, providerCode, modelCode, customEndpoint);
        if (registryRuntime.isPresent()) {
            var runtime = registryRuntime.get();
            long start = System.currentTimeMillis();
            LlmResponse response = callProvider(runtime, conversationId, userMessage, scenario, retrievalResults, citations, List.of(), agentId);
            if (!response.isError()) {
                log.debug("registry LLM 调用成功: providerType={} providerCode={} model={} latency={}",
                        providerType, runtime.provider().getProviderCode(), runtime.defaultModel(),
                        System.currentTimeMillis() - start);
                return response;
            }
            log.warn("registry LLM 调用失败，尝试备选供应商链: providerType={} providerCode={} err={}",
                    providerType, runtime.provider().getProviderCode(),
                    response.error() != null ? response.error().getMessage() : "unknown");
            // 尝试同 providerType 下的备选供应商（健康度+优先级排序）
            String effectiveProviderType = normalizeProviderType(providerType, providerCode);
            String effectiveModelType = normalizeModelType(modelType);
            List<AiProvider> candidates = providerRegistry.resolve(effectiveProviderType, effectiveModelType);
            for (AiProvider cand : candidates) {
                if (cand.getProviderCode() != null && cand.getProviderCode().equals(runtime.provider().getProviderCode())) {
                    continue;
                }
                String candApiKey = providerSelector.resolveApiKey(cand.getApiKeyRef());
                String protocol = cand.getProtocol() == null || cand.getProtocol().isBlank()
                        ? com.yutong.ai.chat.service.llm.OpenAiCompatibleAdapter.PROTOCOL : cand.getProtocol();
                // dify/coze CUSTOM 协议暂按 OPENAI_COMPATIBLE 透传，日志提示
                if (("dify".equalsIgnoreCase(effectiveProviderType) || "coze".equalsIgnoreCase(effectiveProviderType))
                        && !"OPENAI_COMPATIBLE".equalsIgnoreCase(protocol)) {
                    log.debug("dify/coze 供应商使用 OPENAI_COMPATIBLE 透传: providerCode={} protocol={}",
                            cand.getProviderCode(), protocol);
                    protocol = com.yutong.ai.chat.service.llm.OpenAiCompatibleAdapter.PROTOCOL;
                }
                String effModel = resolveEffectiveModel(modelCode, cand, effectiveProviderType);
                if (effModel == null || effModel.isBlank()) continue;
                var candAdapter = providerSelector.buildAdapter(protocol, cand, candApiKey);
                if (candAdapter == null) continue;
                var candRuntime = new LlmProviderSelector.ProviderRuntime(cand, candAdapter, effModel);
                LlmResponse candResp = callProvider(candRuntime, conversationId, userMessage, scenario, retrievalResults, citations, List.of(), agentId);
                if (!candResp.isError()) {
                    return candResp;
                }
                log.warn("备选 registry 供应商失败: providerCode={} model={} err={}",
                        cand.getProviderCode(), effModel,
                        candResp.error() != null ? candResp.error().getMessage() : "unknown");
            }
            // 全部 registry 候选失败，回退到旧 selector 链
            LlmResponse nextResponse = tryNextEnabledProvider(
                    runtime.provider().getProviderCode(), conversationId, userMessage, scenario,
                    retrievalResults, citations, agentId);
            if (nextResponse != null && !nextResponse.isError()) {
                return nextResponse;
            }
            log.warn("所有真实 LLM 供应商均失败: lastProvider={} model={} err={}",
                    runtime.provider().getProviderCode(), runtime.defaultModel(),
                    response.error() != null ? response.error().getMessage() : "unknown");
            return response;
        }

        // 2) 无 registry 匹配，回退到旧 selector（按 providerCode 精确）
        var providerOpt = providerSelector.selectProvider(providerCode, modelCode);
        if (providerOpt.isEmpty()) {
            return LlmResponse.error("none", modelCode == null || modelCode.isBlank() ? "unknown" : modelCode,
                    new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "未配置可用 LLM 供应商"));
        }
        var runtime = providerOpt.get();
        LlmResponse response = callProvider(runtime, conversationId, userMessage, scenario, retrievalResults, citations, List.of(), agentId);
        if (!response.isError()) {
            return response;
        }
        LlmResponse nextResponse = tryNextEnabledProvider(
                runtime.provider().getProviderCode(), conversationId, userMessage, scenario,
                retrievalResults, citations, agentId);
        if (nextResponse != null && !nextResponse.isError()) {
            return nextResponse;
        }
        log.warn("所有真实 LLM 供应商均失败: lastProvider={} model={} err={}",
                runtime.provider().getProviderCode(), runtime.defaultModel(),
                response.error() != null ? response.error().getMessage() : "unknown");
        return response;
    }

    /**
     * 按 providerType/modelType 优先经 registry 解析真实供应商运行时；支持 custom_api 透传 endpoint/model。
     */
    private java.util.Optional<LlmProviderSelector.ProviderRuntime> resolveRuntime(
            String providerType, String modelType, String providerCode, String modelCode, String customEndpoint) {
        String effectiveProviderType = normalizeProviderType(providerType, providerCode);
        String effectiveModelType = normalizeModelType(modelType);

        // custom_api 透传：若请求携带 endpoint 则构造临时 provider，modelCode 直接透传
        if ("custom_api".equalsIgnoreCase(effectiveProviderType) && customEndpoint != null && !customEndpoint.isBlank()) {
            AiProvider ephemeral = new AiProvider();
            ephemeral.setId(IdGenerator.nextId());
            ephemeral.setTenantId(CurrentUserContext.getTenantId());
            ephemeral.setProviderCode("custom_api");
            ephemeral.setProviderName("Custom API (透传)");
            ephemeral.setProviderType("custom_api");
            ephemeral.setModelType(effectiveModelType);
            ephemeral.setEndpoint(customEndpoint.trim());
            // 尝试复用已配置的 custom_api 供应商的 apiKeyRef
            String apiKeyRef = "";
            try {
                var existing = providerRegistry.selectPrimary("custom_api", effectiveModelType);
                if (existing.isPresent() && existing.get().getApiKeyRef() != null) {
                    apiKeyRef = existing.get().getApiKeyRef();
                }
            } catch (Exception e) {
                log.debug("custom_api 透传查询已有供应商失败: {}", e.getMessage());
            }
            ephemeral.setApiKeyRef(apiKeyRef);
            ephemeral.setProtocol(com.yutong.ai.chat.service.llm.OpenAiCompatibleAdapter.PROTOCOL);
            ephemeral.setEnabled(true);
            ephemeral.setPriority(0);
            String effectiveModel = (modelCode != null && !modelCode.isBlank()) ? modelCode.trim() : "custom-model";
            ephemeral.setModelListJson("[{\"code\":\"" + effectiveModel + "\",\"name\":\"" + effectiveModel + "\"}]");
            String apiKey = providerSelector.resolveApiKey(ephemeral.getApiKeyRef());
            var adapter = providerSelector.buildAdapter(ephemeral.getProtocol(), ephemeral, apiKey);
            if (adapter != null) {
                return java.util.Optional.of(new LlmProviderSelector.ProviderRuntime(ephemeral, adapter, effectiveModel));
            }
        }

        if (effectiveProviderType != null) {
            try {
                var opt = providerRegistry.selectPrimary(effectiveProviderType, effectiveModelType);
                if (opt.isPresent()) {
                    AiProvider p = opt.get();
                    // custom_api 且请求有 endpoint 覆盖
                    if ("custom_api".equalsIgnoreCase(effectiveProviderType)
                            && customEndpoint != null && !customEndpoint.isBlank()) {
                        p.setEndpoint(customEndpoint.trim());
                    }
                    String apiKey = providerSelector.resolveApiKey(p.getApiKeyRef());
                    String protocol = p.getProtocol();
                    if (protocol == null || protocol.isBlank()) {
                        protocol = com.yutong.ai.chat.service.llm.OpenAiCompatibleAdapter.PROTOCOL;
                    }
                    // dify/coze 分别适配：当前以 OPENAI_COMPATIBLE 透传实现，后续可替换为专用适配器
                    if (("dify".equalsIgnoreCase(effectiveProviderType) || "coze".equalsIgnoreCase(effectiveProviderType))
                            && !"OPENAI_COMPATIBLE".equalsIgnoreCase(protocol)) {
                        log.info("dify/coze 供应商协议 {} 按 OPENAI_COMPATIBLE 透传: providerCode={}",
                                protocol, p.getProviderCode());
                        protocol = com.yutong.ai.chat.service.llm.OpenAiCompatibleAdapter.PROTOCOL;
                    }
                    String effModel = resolveEffectiveModel(modelCode, p, effectiveProviderType);
                    if (effModel == null || effModel.isBlank()) {
                        effModel = providerSelector.extractDefaultModel(p.getModelListJson());
                    }
                    if (effModel == null || effModel.isBlank()) {
                        effModel = modelCode;
                    }
                    if (effModel != null && !effModel.isBlank()) {
                        var adapter = providerSelector.buildAdapter(protocol, p, apiKey);
                        if (adapter != null) {
                            return java.util.Optional.of(new LlmProviderSelector.ProviderRuntime(p, adapter, effModel));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("registry 解析失败，回退到 selector: providerType={} modelType={} err={}",
                        effectiveProviderType, effectiveModelType, e.getMessage());
            }
        }
        return java.util.Optional.empty();
    }

    private String normalizeProviderType(String providerType, String providerCode) {
        if (providerType != null && !providerType.isBlank()) {
            return providerType.trim().toLowerCase();
        }
        if (providerCode != null && !providerCode.isBlank()) {
            String pc = providerCode.trim().toLowerCase();
            // 若 providerCode 本身就是 11 枚举之一，直接作为 providerType
            if (com.yutong.ai.gateway.domain.AiProviderType.isValid(pc)) {
                return pc;
            }
        }
        return null;
    }

    private String normalizeModelType(String modelType) {
        if (modelType == null || modelType.isBlank()) {
            return "chat";
        }
        return modelType.trim().toLowerCase();
    }

    private String resolveEffectiveModel(String requestedModelCode, AiProvider provider, String effectiveProviderType) {
        if (requestedModelCode == null || requestedModelCode.isBlank()) {
            return providerSelector.extractDefaultModel(provider.getModelListJson());
        }
        // custom_api 透传：不校验 modelListJson，直接使用请求的 modelCode
        if ("custom_api".equalsIgnoreCase(effectiveProviderType)) {
            return requestedModelCode.trim();
        }
        // 其余类型：校验是否在 modelListJson 中，存在则直接用，否则回退默认
        String normalized = requestedModelCode.trim();
        if (provider.getModelListJson() != null && !provider.getModelListJson().isBlank()) {
            try {
                com.fasterxml.jackson.databind.JsonNode arr = objectMapper.readTree(provider.getModelListJson());
                if (arr.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : arr) {
                        if (normalized.equals(node.path("code").asText(null))) {
                            return normalized;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("modelListJson 解析失败，按默认处理: {}", e.getMessage());
            }
        }
        log.debug("请求模型不在供应商列表中，使用默认模型: requested={} providerCode={}",
                normalized, provider.getProviderCode());
        return providerSelector.extractDefaultModel(provider.getModelListJson());
    }

    /**
     * 依次尝试当前租户所有启用的非 mock 供应商（跳过指定已失败的供应商），直到成功或耗尽。
     */
    private LlmResponse tryNextEnabledProvider(String excludeProviderCode, String conversationId,
                                               String userMessage, String scenario,
                                               List<RagRetrievalService.RetrievalResult> retrievalResults,
                                               List<AiChatVO.Citation> citations,
                                               String agentId) {
        List<LlmProviderSelector.ProviderRuntime> providers = providerSelector.selectEnabledProviders();
        for (LlmProviderSelector.ProviderRuntime runtime : providers) {
            String code = runtime.provider().getProviderCode();
            if (code == null || code.equals(excludeProviderCode)) {
                continue;
            }
            LlmResponse response = callProvider(runtime, conversationId, userMessage, scenario, retrievalResults, citations, List.of(), agentId);
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
                                     List<AiChatVO.Citation> citations,
                                     List<String> imageUrls,
                                     String agentId) {
        List<LlmMessage> messages = buildLlmMessages(
                runtime.provider(), conversationId, userMessage, scenario, retrievalResults, citations, imageUrls, agentId);
        LlmRequest llmRequest = new LlmRequest(runtime.defaultModel(), messages, 0.7, null, false, scenario);
        return runtime.adapter().chat(llmRequest);
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
                                              List<AiChatVO.Citation> citations,
                                              List<String> imageUrls,
                                              String agentId) {
        String systemPrompt = buildSystemPrompt(scenario, retrievalResults, citations, agentId);

        // 匿名免 Key 供应商（Pollinations 等）通常只接受单条 user 消息，且对内容长度/格式敏感。
        // 折叠 system prompt 会显著增加内容长度并触发其免费额度限制，因此仅发送原始用户消息，
        // 由 OpenAiCompatibleAdapter 再做长度截断兜底（P6-02 免费 LLM 供应商集成）。
        // 匿名 provider 不支持多模态（没有视觉模型），仍走纯文本
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
        // 当前用户消息：若有图片 URL 走 OpenAI 多模态 content（P1-7），否则纯文本
        LlmMessage currentUser = (imageUrls == null || imageUrls.isEmpty())
                ? LlmMessage.user(userMessage)
                : LlmMessage.userMultimodal(userMessage, imageUrls);
        messages.add(currentUser);
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

    /**
     * 解析聊天请求中的 agentId 为系统提示块 (P2-G AgentSelect 闭环)。
     * <ul>
     *   <li>agentId 为空 → null (默认助手, 行为不变);</li>
     *   <li>Agent 不存在 → 404 (AgentService.getAgent 抛 ResourceNotFoundException);</li>
     *   <li>Agent 未发布 (非 PUBLISHED) → 400, 禁止用草稿 Agent 对话 (失败关闭);</li>
     *   <li>已发布 → 返回身份 + systemPrompt 块, 由 buildSystemPrompt 置于基础安全规则之前。</li>
     * </ul>
     */
    String resolveAgentPromptBlock(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return null;
        }
        AiAgent agent = agentService.getAgent(agentId.trim());
        if (!AiAgent.STATUS_PUBLISHED.equals(agent.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "Agent 未发布, 不可用于对话: " + agentId.trim());
        }
        return buildAgentPromptBlock(agent);
    }

    /**
     * 构造 Agent 身份提示块 (纯函数, 便于单测)。
     */
    static String buildAgentPromptBlock(AiAgent agent) {
        StringBuilder sb = new StringBuilder();
        sb.append("当前 Agent: ").append(nvl(agent.getAgentName())).append(" (").append(nvl(agent.getAgentCode())).append(")\n");
        if (agent.getSystemPrompt() != null && !agent.getSystemPrompt().isBlank()) {
            sb.append(agent.getSystemPrompt().trim()).append("\n");
        }
        return sb.toString();
    }

    private String buildSystemPrompt(String scenario,
                                     List<RagRetrievalService.RetrievalResult> retrievalResults,
                                     List<AiChatVO.Citation> citations) {
        return buildSystemPrompt(scenario, retrievalResults, citations, null);
    }

    private String buildSystemPrompt(String scenario,
                                     List<RagRetrievalService.RetrievalResult> retrievalResults,
                                     List<AiChatVO.Citation> citations,
                                     String agentId) {
        StringBuilder sb = new StringBuilder();
        String agentBlock = resolveAgentPromptBlock(agentId);
        if (agentBlock != null) {
            sb.append(agentBlock);
        }
        sb.append("你是 YuTong 平台的 AI 助手，基于企业知识库为用户提供专业、准确的回答。\n");
        sb.append("规则：\n");
        sb.append("1. 仅基于提供的知识库内容回答，不确定时明确说明。\n");
        sb.append("2. 禁止泄露用户密码、密钥、token 等敏感信息。\n");
        sb.append("3. 涉及修改生产数据的建议必须说明需要人工确认。\n");
        if (scenario != null && !scenario.isBlank()) {
            sb.append("当前场景: ").append(scenario).append("\n");
        }
        if (aiMemoryService != null) {
            try {
                String recalled = aiMemoryService.recallForChat(CurrentUserContext.getUserId());
                if (recalled != null && !recalled.isBlank()) {
                    sb.append('\n').append(recalled);
                }
            } catch (Exception e) {
                log.debug("memory recall skipped: {}", e.getMessage());
            }
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
