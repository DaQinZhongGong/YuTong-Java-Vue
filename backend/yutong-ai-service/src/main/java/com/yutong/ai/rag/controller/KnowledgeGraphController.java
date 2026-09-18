package com.yutong.ai.rag.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.ai.rag.domain.AiKnowledgeGraph;
import com.yutong.ai.rag.domain.AiKnowledgeGraphSegment;
import com.yutong.ai.rag.service.KnowledgeGraphService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

/**
 * 知识图谱接口。设计来源: V043 P0 平价能力 — create/build/query
 */
@Tag(name = "AI-知识图谱")
@RestController
@RequestMapping("/api/v1/knowledge-graph")
public class KnowledgeGraphController {

    private final KnowledgeGraphService graphService;

    public KnowledgeGraphController(KnowledgeGraphService graphService) {
        this.graphService = graphService;
    }

    @Operation(summary = "分页查询知识图谱", operationId = "pageKnowledgeGraphs")
    @RequiresPermission("ai:knowledge-graph:list")
    @GetMapping
    public Result<PageResult<AiKnowledgeGraph>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kbId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return Result.ok(graphService.list(PageRequest.of(page, size), kbId, status, keyword),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询知识图谱详情", operationId = "getKnowledgeGraph")
    @RequiresPermission("ai:knowledge-graph:detail")
    @GetMapping("/{id}")
    public Result<AiKnowledgeGraph> get(@PathVariable String id) {
        return Result.ok(graphService.get(id), TraceContext.getTraceId());
    }

    @Operation(summary = "查询知识图谱分段抽取结果", operationId = "listKnowledgeGraphSegments")
    @RequiresPermission("ai:knowledge-graph:detail")
    @GetMapping("/{id}/segments")
    public Result<List<AiKnowledgeGraphSegment>> listSegments(@PathVariable String id) {
        return Result.ok(graphService.listSegments(id), TraceContext.getTraceId());
    }

    @Operation(summary = "创建知识图谱（DRAFT）", operationId = "createKnowledgeGraph")
    @RequiresPermission("ai:knowledge-graph:add")
    @Auditable(operationType = "CREATE", module = "ai", bizType = "ai_knowledge_graph", content = "创建知识图谱")
    @PostMapping
    public Result<AiKnowledgeGraph> create(@Valid @RequestBody AiKnowledgeGraph graph) {
        return Result.ok(graphService.create(graph), TraceContext.getTraceId());
    }

    @Operation(summary = "构建知识图谱（DRAFT/FAILED→BUILDING，LLM 抽取实体关系）", operationId = "buildKnowledgeGraph")
    @RequiresPermission("ai:knowledge-graph:edit")
    @Auditable(operationType = "BUILD", module = "ai", bizType = "ai_knowledge_graph", bizIdExpr = "#id", content = "构建知识图谱")
    @PostMapping("/{id}/build")
    public Result<AiKnowledgeGraph> build(@PathVariable String id) {
        return Result.ok(graphService.build(id), TraceContext.getTraceId());
    }

    @Operation(summary = "查询知识图谱（聚合）", operationId = "queryKnowledgeGraph")
    @RequiresPermission("ai:knowledge-graph:detail")
    @GetMapping("/{id}/query")
    public Result<AiKnowledgeGraph> query(@PathVariable String id) {
        return Result.ok(graphService.queryWithSegments(id), TraceContext.getTraceId());
    }
}
