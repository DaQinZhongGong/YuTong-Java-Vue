package com.yutong.ai.gateway.controller;

import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.service.AiProviderApplicationService;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * AI 供应商接口。设计来源: 13-AI能力设计、08-API契约设计
 */
@Hidden
@Tag(name = "AI-供应商管理")
@RestController
@RequestMapping("/api/v1/ai/providers")
public class AiProviderController {

    private final AiProviderApplicationService service;

    public AiProviderController(AiProviderApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询供应商", operationId = "listAiProviders")
    @RequiresPermission("ai:provider:list")
    @GetMapping
    public Result<PageResult<AiProvider>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Boolean enabled) {
        return Result.ok(service.pageProviders(PageRequest.of(page, size), providerCode, status, keyword, enabled),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询供应商详情", operationId = "getAiProvider")
    @GetMapping("/{id}")
    public Result<AiProvider> get(@PathVariable String id) {
        return Result.ok(service.getProvider(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建供应商", operationId = "createAiProvider")
    @PostMapping
    public Result<AiProvider> create(@RequestBody AiProvider provider) {
        return Result.ok(service.saveDraft(provider), TraceContext.getTraceId());
    }

    @Operation(summary = "更新供应商", operationId = "updateAiProvider")
    @PutMapping("/{id}")
    public Result<AiProvider> update(@PathVariable String id, @RequestBody AiProvider provider) {
        provider.setId(id);
        return Result.ok(service.saveDraft(provider), TraceContext.getTraceId());
    }

    @Operation(summary = "启用供应商", operationId = "enableAiProvider")
    @PostMapping("/{id}/enable")
    public Result<AiProvider> enable(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.enable(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "禁用供应商", operationId = "disableAiProvider")
    @RequiresPermission("ai:provider:edit")
    @PostMapping("/{id}/disable")
    public Result<AiProvider> disable(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.disable(id, version), TraceContext.getTraceId());
    }
}
