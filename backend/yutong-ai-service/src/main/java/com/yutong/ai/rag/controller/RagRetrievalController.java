package com.yutong.ai.rag.controller;

import com.yutong.ai.rag.dto.RagRetrievalRequest;
import com.yutong.ai.rag.service.RagRetrievalService;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.response.Result;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG 检索接口。设计来源: 13-AI能力设计、08-API契约设计
 */
@Hidden
@Tag(name = "AI-RAG检索")
@RestController
@RequestMapping("/api/v1/ai/rag")
public class RagRetrievalController {

    private final RagRetrievalService service;

    public RagRetrievalController(RagRetrievalService service) {
        this.service = service;
    }

    @Operation(summary = "检索相关分块", operationId = "retrieveRagChunks")
    @RequiresPermission("ai:rag:retrieve")
    @PostMapping("/retrieve")
    public Result<List<RagRetrievalService.RetrievalResult>> retrieve(@RequestBody RagRetrievalRequest request) {
        int topK = request.topK() != null ? request.topK() : 5;
        List<RagRetrievalService.RetrievalResult> results = service.retrieve(
                CurrentUserContext.getTenantId(),
                request.kbId(),
                request.query(),
                CurrentUserContext.getUserId(),
                topK);
        return Result.ok(results, TraceContext.getTraceId());
    }
}
