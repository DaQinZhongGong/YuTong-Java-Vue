package com.yutong.ai.copilot.controller;

import com.yutong.ai.copilot.domain.AiCopilotRun;
import com.yutong.ai.copilot.dto.AiCopilotDraftRequest;
import com.yutong.ai.copilot.dto.AiCopilotDraftResponse;
import com.yutong.ai.copilot.service.AiCopilotDraftService;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AI-Copilot草稿")
@RestController
@RequestMapping("/api/v1/ai/copilot")
public class AiCopilotController {

    private final AiCopilotDraftService draftService;

    public AiCopilotController(AiCopilotDraftService draftService) {
        this.draftService = draftService;
    }

    @Operation(summary = "生成低代码草稿（LLM，不落库）", operationId = "generateAiCopilotDraft")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/generate")
    public Result<AiCopilotDraftResponse> generate(@Valid @RequestBody AiCopilotDraftRequest request) {
        return Result.ok(draftService.generate(request), TraceContext.getTraceId());
    }

    @Operation(summary = "分页查询 Copilot Run", operationId = "pageAiCopilotRuns")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/runs")
    public Result<PageResult<AiCopilotRun>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return Result.ok(draftService.page(PageRequest.of(page, size), status), TraceContext.getTraceId());
    }

    @Operation(summary = "批准 Copilot 草稿（仍不落低代码库）", operationId = "approveAiCopilotRun")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/runs/{id}/approve")
    public Result<AiCopilotRun> approve(@PathVariable String id) {
        return Result.ok(draftService.approve(id), TraceContext.getTraceId());
    }

    @Operation(summary = "驳回 Copilot 草稿", operationId = "rejectAiCopilotRun")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/runs/{id}/reject")
    public Result<AiCopilotRun> reject(@PathVariable String id) {
        return Result.ok(draftService.reject(id), TraceContext.getTraceId());
    }

    @Operation(summary = "拉取未确认 Copilot Outbox", operationId = "listAiCopilotOutbox")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/outbox")
    public Result<List<AiCopilotRun>> outbox() {
        return Result.ok(draftService.pendingOutbox(), TraceContext.getTraceId());
    }

    @Operation(summary = "确认 Copilot Outbox 已消费", operationId = "ackAiCopilotOutbox")
    @RequiresPermission("ai:assistant:use")
    @PostMapping("/outbox/{id}/ack")
    public Result<AiCopilotRun> ack(@PathVariable String id) {
        return Result.ok(draftService.ackOutbox(id), TraceContext.getTraceId());
    }
}
