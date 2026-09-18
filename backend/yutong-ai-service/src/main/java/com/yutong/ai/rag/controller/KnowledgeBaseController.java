package com.yutong.ai.rag.controller;

import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.dto.UpdateRetrievalConfigRequest;
import com.yutong.ai.rag.service.KnowledgeBaseApplicationService;
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
 * 知识库管理接口。设计来源: 13-AI能力设计、08-API契约设计
 */
@Hidden
@Tag(name = "AI-知识库管理")
@RestController
@RequestMapping("/api/v1/ai/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseApplicationService service;

    public KnowledgeBaseController(KnowledgeBaseApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询知识库", operationId = "listAiKnowledgeBases")
    @GetMapping
    public Result<PageResult<AiKnowledgeBase>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kbCode,
            @RequestParam(required = false) String status) {
        return Result.ok(service.pageKnowledgeBases(PageRequest.of(page, size), kbCode, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询知识库详情", operationId = "getAiKnowledgeBase")
    @GetMapping("/{id}")
    public Result<AiKnowledgeBase> get(@PathVariable String id) {
        return Result.ok(service.getKnowledgeBase(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建知识库草稿", operationId = "createAiKnowledgeBase")
    @RequiresPermission("ai:knowledge-base:add")
    @PostMapping
    public Result<AiKnowledgeBase> create(@RequestBody AiKnowledgeBase kb) {
        return Result.ok(service.saveDraft(kb), TraceContext.getTraceId());
    }

    @Operation(summary = "更新知识库草稿", operationId = "updateAiKnowledgeBase")
    @PutMapping("/{id}")
    public Result<AiKnowledgeBase> update(@PathVariable String id, @RequestBody AiKnowledgeBase kb) {
        kb.setId(id);
        return Result.ok(service.saveDraft(kb), TraceContext.getTraceId());
    }

    @Operation(summary = "发布知识库", operationId = "publishAiKnowledgeBase")
    @PostMapping("/{id}/publish")
    public Result<AiKnowledgeBase> publish(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.publish(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "禁用知识库", operationId = "disableAiKnowledgeBase")
    @RequiresPermission("ai:knowledge-base:edit")
    @PostMapping("/{id}/disable")
    public Result<AiKnowledgeBase> disable(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.disable(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "更新检索配置 (混合权重/数量/门限, ACTIVE 可直接调参实时生效)",
            operationId = "updateAiKnowledgeBaseRetrievalConfig")
    @RequiresPermission("ai:knowledge-base:edit")
    @PatchMapping("/{id}/retrieval-config")
    public Result<AiKnowledgeBase> updateRetrievalConfig(
            @PathVariable String id, @RequestBody UpdateRetrievalConfigRequest req) {
        return Result.ok(service.updateRetrievalConfig(
                id, req.getHybridEnabled(), req.getHybridVectorWeight(),
                req.getHybridTopK(), req.getHybridMinScore()), TraceContext.getTraceId());
    }
}
