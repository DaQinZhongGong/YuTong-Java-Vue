package com.yutong.ai.gateway.controller;

import com.yutong.ai.gateway.domain.AiPromptTemplate;
import com.yutong.ai.gateway.service.AiPromptApplicationService;
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
 * Prompt 模板接口。设计来源: 13-AI能力设计、08-API契约设计
 */
@Hidden
@Tag(name = "AI-Prompt模板管理")
@RestController
@RequestMapping("/api/v1/ai/prompt-templates")
public class AiPromptTemplateController {

    private final AiPromptApplicationService service;

    public AiPromptTemplateController(AiPromptApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询 Prompt 模板", operationId = "listAiPromptTemplates")
    @GetMapping
    public Result<PageResult<AiPromptTemplate>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String templateCode,
            @RequestParam(required = false) String scenario,
            @RequestParam(required = false) String status) {
        return Result.ok(service.pageTemplates(PageRequest.of(page, size), templateCode, scenario, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询模板详情", operationId = "getAiPromptTemplate")
    @GetMapping("/{id}")
    public Result<AiPromptTemplate> get(@PathVariable String id) {
        return Result.ok(service.getTemplate(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建模板草稿", operationId = "createAiPromptTemplate")
    @PostMapping
    public Result<AiPromptTemplate> create(@RequestBody AiPromptTemplate template) {
        return Result.ok(service.saveDraft(template), TraceContext.getTraceId());
    }

    @Operation(summary = "更新模板", operationId = "updateAiPromptTemplate")
    @RequiresPermission("ai:prompt-template:edit")
    @PutMapping("/{id}")
    public Result<AiPromptTemplate> update(@PathVariable String id, @RequestBody AiPromptTemplate template) {
        template.setId(id);
        return Result.ok(service.saveDraft(template), TraceContext.getTraceId());
    }

    @Operation(summary = "发布模板", operationId = "publishAiPromptTemplate")
    @RequiresPermission("ai:prompt-template:edit")
    @PostMapping("/{id}/publish")
    public Result<AiPromptTemplate> publish(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.publish(id, version), TraceContext.getTraceId());
    }
}
