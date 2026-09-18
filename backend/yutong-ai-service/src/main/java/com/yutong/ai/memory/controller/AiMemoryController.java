package com.yutong.ai.memory.controller;

import com.yutong.ai.memory.domain.AiMemory;
import com.yutong.ai.memory.dto.SaveMemoryRequest;
import com.yutong.ai.memory.service.AiMemoryService;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 跨会话记忆管理。短期记忆仍走会话窗口；本接口管理长期/用户/全局层。
 */
@Tag(name = "AI-记忆管理")
@RestController
@RequestMapping("/api/v1/ai/memories")
public class AiMemoryController {

    private final AiMemoryService memoryService;

    public AiMemoryController(AiMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @Operation(summary = "分页查询记忆", operationId = "pageAiMemories")
    @RequiresPermission("ai:assistant:use")
    @GetMapping
    public Result<PageResult<AiMemory>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String ownerType,
            @RequestParam(required = false) String ownerId,
            @RequestParam(required = false) String memoryKind) {
        return Result.ok(memoryService.page(PageRequest.of(page, size), ownerType, ownerId, memoryKind),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询记忆详情", operationId = "getAiMemory")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/{id}")
    public Result<AiMemory> get(@PathVariable String id) {
        return Result.ok(memoryService.get(id), TraceContext.getTraceId());
    }

    @Operation(summary = "写入记忆", operationId = "saveAiMemory")
    @RequiresPermission("ai:assistant:use")
    @PostMapping
    public Result<AiMemory> save(@Valid @RequestBody SaveMemoryRequest request) {
        return Result.ok(memoryService.save(request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除记忆", operationId = "deleteAiMemory")
    @RequiresPermission("ai:assistant:use")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        memoryService.delete(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "召回当前用户可注入对话的记忆", operationId = "recallAiMemories")
    @RequiresPermission("ai:assistant:use")
    @GetMapping("/recall")
    public Result<String> recall() {
        return Result.ok(memoryService.recallForChat(null), TraceContext.getTraceId());
    }
}
