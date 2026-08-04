package com.yutong.ai.chat.controller;

import com.yutong.ai.chat.dto.AiChatRequest;
import com.yutong.ai.chat.dto.AiChatVO;
import com.yutong.ai.chat.dto.ApplySuggestionRequest;
import com.yutong.ai.chat.service.AiChatApplicationService;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.gateway.domain.AiConversation;
import com.yutong.ai.gateway.domain.AiMessage;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.idempotency.Idempotent;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * AI 对话接口。设计来源: 13-AI能力设计 chatWithAssistant / applyAiSuggestion、08-API契约设计
 * <p>
 * 安全约束:
 * - AI 仅返回草稿建议，不直接修改生产数据
 * - 应用建议必须回传 schema/hash/version/idempotencyKey
 * <p>
 * GA2-31 内容协商: POST /api/v1/ai/chat 同时支持两种响应
 * - Accept: application/json → 完整响应 Result&lt;AiChatVO&gt;
 * - Accept: text/event-stream → SSE 流式响应（meta/delta/citation/done/error 事件序列）
 * 设计来源: 13 号文档 line 252、contracts/openapi/openapi.yaml AiChatResponse (line 1289-1295)
 */
@Tag(name = "AI-对话与建议")
@RestController
@RequestMapping("/api/v1/ai")
public class AiChatController {

    private final AiChatApplicationService service;

    public AiChatController(AiChatApplicationService service) {
        this.service = service;
    }

    /**
     * AI 对话（完整响应）。Accept: application/json 时命中。
     * 第一版默认走此路径，返回完整 Result&lt;AiChatVO&gt;。
     */
    @Operation(summary = "AI 对话", operationId = "chatWithAssistant")
    @RequiresPermission("ai:assistant:use")
    @Auditable(operationType = "CHAT", module = "ai", bizType = "ai_chat",
            bizIdExpr = "#result.data.conversationId", content = "AI 对话")
    @Idempotent(resourceType = "ai-chat", resourceIdExpr = "#request.conversationId",
            action = "CHAT", ttlSeconds = 30)
    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE)
    public Result<AiChatVO> chat(@RequestBody AiChatRequest request) {
        return Result.ok(service.chat(request), TraceContext.getTraceId());
    }

    /**
     * AI 对话（SSE 流式响应）。Accept: text/event-stream 时命中。
     * <p>
     * GA2-31 落地: 设计来源 13-AI能力设计 line 137-143/252、contracts/openapi/openapi.yaml AiStreamEvent。
     * 事件序列: meta → (citation)* → (delta)+ → done | error
     * <p>
     * 注意: Spring MVC 根据 Accept 头和 produces 属性路由到对应方法。
     * 若 Accept 头未指定或为 application/json，走上面的 chat() 方法。
     */
    @Operation(summary = "AI 对话（SSE 流式）", operationId = "chatWithAssistantStream")
    @RequiresPermission("ai:assistant:use")
    @Auditable(operationType = "CHAT", module = "ai", bizType = "ai_chat", content = "AI 对话(SSE流式)")
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody AiChatRequest request) {
        return service.streamChat(request);
    }

    @Operation(summary = "查询可用的 LLM 模型列表", operationId = "listAiModels")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/providers/models")
    public Result<List<LlmProviderSelector.ModelOption>> listModels() {
        return Result.ok(service.listAvailableModels(), TraceContext.getTraceId());
    }

    @Operation(summary = "分页查询会话列表", operationId = "listAiConversations")
    @RequiresPermission("ai:conversation:list")
    @GetMapping("/conversations")
    public Result<PageResult<AiConversation>> pageConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String scenario) {
        return Result.ok(service.pageConversations(PageRequest.of(page, size), scenario),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询会话详情", operationId = "getAiConversation")
    @RequiresPermission("ai:conversation:detail")
    @GetMapping("/conversations/{id}")
    public Result<AiConversation> getConversation(@PathVariable String id) {
        return Result.ok(service.getConversation(id), TraceContext.getTraceId());
    }

    @Hidden
    @Operation(summary = "查询会话消息列表", operationId = "listAiConversationMessages")
    @RequiresPermission("ai:conversation:detail")
    @GetMapping("/conversations/{id}/messages")
    public Result<List<AiMessage>> listMessages(@PathVariable String id) {
        return Result.ok(service.getConversationMessages(id), TraceContext.getTraceId());
    }

    @Operation(summary = "应用 AI 建议（进入草稿区）", operationId = "applyAiSuggestion")
    @RequiresPermission("ai:suggestion:apply")
    @Auditable(operationType = "APPLY", module = "ai", bizType = "ai_suggestion",
            bizIdExpr = "#request.draftId", content = "应用 AI 建议")
    @Idempotent(resourceType = "ai-apply", resourceIdExpr = "#request.draftId",
            action = "APPLY", ttlSeconds = 30)
    @PostMapping("/suggestions/apply")
    public Result<String> applySuggestion(@RequestBody ApplySuggestionRequest request) {
        return Result.ok(service.applySuggestion(request), TraceContext.getTraceId());
    }
}
