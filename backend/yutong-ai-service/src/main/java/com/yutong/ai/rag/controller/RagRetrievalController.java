package com.yutong.ai.rag.controller;

import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.dto.MultimodalEmbeddingRequest;
import com.yutong.ai.rag.dto.MultimodalEmbeddingResponse;
import com.yutong.ai.rag.dto.RagRetrievalRequest;
import com.yutong.ai.rag.mapper.AiKnowledgeBaseMapper;
import com.yutong.ai.rag.service.EmbeddingService;
import com.yutong.ai.rag.service.RagRetrievalService;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.ResourceNotFoundException;
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
 * V061: 多模态 Embedding 测试端点（管理端调参，不返回完整向量）。
 */
@Hidden
@Tag(name = "AI-RAG检索")
@RestController
@RequestMapping("/api/v1/ai/rag")
public class RagRetrievalController {

    private final RagRetrievalService service;
    private final EmbeddingService embeddingService;
    private final AiKnowledgeBaseMapper kbMapper;

    public RagRetrievalController(RagRetrievalService service,
                                  EmbeddingService embeddingService,
                                  AiKnowledgeBaseMapper kbMapper) {
        this.service = service;
        this.embeddingService = embeddingService;
        this.kbMapper = kbMapper;
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

    /**
     * 多模态 Embedding 测试端点 — 返回 dimension + remoteSuccess + 前 8 维预览，不返回完整向量。
     * 设计来源: docs/compose/spec/ai-depth-parity.md S2.3
     */
    @Operation(summary = "多模态 Embedding 测试", operationId = "testMultimodalEmbedding")
    @RequiresPermission("ai:rag:retrieve")
    @PostMapping("/embeddings/multimodal")
    public Result<MultimodalEmbeddingResponse> embedMultimodal(@RequestBody MultimodalEmbeddingRequest request) {
        if (request == null || request.modality() == null || request.modality().isBlank()
                || request.payload() == null || request.payload().isBlank()) {
            throw new com.yutong.common.exception.BusinessException(
                    com.yutong.common.errorcode.ErrorCode.SYS_PARAM_INVALID,
                    "modality and payload are required");
        }
        AiKnowledgeBase kb = null;
        if (request.knowledgeBaseId() != null && !request.knowledgeBaseId().isBlank()) {
            String tenantId = com.yutong.common.auth.CurrentUserContext.getTenantId();
            if (tenantId == null || tenantId.isBlank()) {
                throw new com.yutong.common.exception.BusinessException(
                        com.yutong.common.errorcode.ErrorCode.SYS_UNAUTHORIZED, "缺失租户上下文");
            }
            kb = kbMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiKnowledgeBase>()
                    .eq(AiKnowledgeBase::getId, request.knowledgeBaseId())
                    .eq(AiKnowledgeBase::getTenantId, tenantId)
                    .last("LIMIT 1"));
            if (kb == null) {
                throw new ResourceNotFoundException("knowledge base not found: " + request.knowledgeBaseId());
            }
        }
        EmbeddingService.MultimodalEmbedOutcome outcome =
                embeddingService.embedMultimodalOutcome(request.modality(), request.payload(), kb);
        int dim = outcome.vector() != null ? outcome.vector().length : embeddingService.resolveTargetDimension(kb);
        List<Float> preview = embeddingService.preview(outcome.vector(), 8);
        MultimodalEmbeddingResponse body = new MultimodalEmbeddingResponse(
                request.modality().trim().toLowerCase(),
                dim,
                outcome.remoteSuccess(),
                preview,
                outcome.message());
        return Result.ok(body, TraceContext.getTraceId());
    }
}
