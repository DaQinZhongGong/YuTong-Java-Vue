package com.yutong.ai.gateway.controller;

import com.yutong.ai.gateway.domain.AiToolCallLog;
import com.yutong.ai.gateway.service.AiAuditService;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 工具调用审计日志接口。设计来源: 13-AI能力设计、08-API契约设计
 */
@Tag(name = "AI-工具调用审计")
@RestController
@RequestMapping("/api/v1/ai/tool-call-logs")
public class AiToolCallLogController {

    private final AiAuditService service;

    public AiToolCallLogController(AiAuditService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询工具调用审计日志", operationId = "listAiToolCallLogs")
    @RequiresPermission("ai:tool-log:list")
    @GetMapping
    public Result<PageResult<AiToolCallLog>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String toolName,
            @RequestParam(required = false) String result) {
        return Result.ok(service.pageToolCallLogs(PageRequest.of(page, size), userId, toolName, result),
                TraceContext.getTraceId());
    }
}
