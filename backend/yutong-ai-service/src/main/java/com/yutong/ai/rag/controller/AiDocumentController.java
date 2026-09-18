package com.yutong.ai.rag.controller;

import com.yutong.ai.rag.domain.AiDocument;
import com.yutong.ai.rag.dto.IngestDocumentRequest;
import com.yutong.ai.rag.dto.IngestFileRequest;
import com.yutong.ai.rag.service.DocumentIngestApplicationService;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 文档入库管理接口。设计来源: 13-AI能力设计、08-API契约设计
 * 受控入库端点: POST /ingest
 */
@Hidden
@Tag(name = "AI-文档入库管理")
@RestController
@RequestMapping("/api/v1/ai/documents")
public class AiDocumentController {

    private final DocumentIngestApplicationService service;

    public AiDocumentController(DocumentIngestApplicationService service) {
        this.service = service;
    }

    @Operation(summary = "分页查询文档", operationId = "listAiDocuments")
    @RequiresPermission("ai:document:list")
    @GetMapping
    public Result<PageResult<AiDocument>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kbId,
            @RequestParam(required = false) String docTitle,
            @RequestParam(required = false) String documentStatus) {
        return Result.ok(service.pageDocuments(PageRequest.of(page, size), kbId, docTitle, documentStatus),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询文档详情", operationId = "getAiDocument")
    @RequiresPermission("ai:document:list")
    @GetMapping("/{id}")
    public Result<AiDocument> get(@PathVariable String id) {
        return Result.ok(service.getDocument(id), TraceContext.getTraceId());
    }

    @Operation(summary = "受控入库文档", operationId = "ingestAiDocument")
    @RequiresPermission("ai:document:add")
    @PostMapping("/ingest")
    public Result<AiDocument> ingest(@RequestBody IngestDocumentRequest request) {
        AiDocument document = service.ingest(
                request.kbId(),
                request.docTitle(),
                request.sourceType(),
                request.sourceUri(),
                request.content(),
                request.visibility(),
                request.sensitivityLevel(),
                request.permissionCode());
        return Result.ok(document, TraceContext.getTraceId());
    }

    @Operation(summary = "基于文件入库文档（P0-1 RAG loader parity）", operationId = "ingestAiDocumentFromFile",
            description = "通过 fileId 从 MinIO 拉取原文件，按 loaderType/后缀自动路由到 pdf/docx/xlsx/csv/md/txt 装载器抽取文本后分片与向量化")
    @RequiresPermission("ai:document:add")
    @PostMapping("/ingest-file")
    public Result<AiDocument> ingestFromFile(@Valid @RequestBody IngestFileRequest request) {
        AiDocument document = service.ingestFromFile(
                request.kbId(),
                request.docTitle(),
                request.fileId(),
                request.loaderType(),
                request.sourceType(),
                request.visibility(),
                request.sensitivityLevel(),
                request.permissionCode());
        return Result.ok(document, TraceContext.getTraceId());
    }

    @Operation(summary = "标记文档失效", operationId = "markAiDocumentStale")
    @RequiresPermission("ai:document:delete")
    @PostMapping("/{id}/mark-stale")
    public Result<AiDocument> markStale(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(service.markStale(id, version), TraceContext.getTraceId());
    }
}
