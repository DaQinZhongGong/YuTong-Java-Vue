package com.yutong.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.ai.gateway.service.AiProviderRegistry;
import com.yutong.ai.rag.domain.AiDocument;
import com.yutong.ai.rag.domain.AiDocumentChunk;
import com.yutong.ai.rag.domain.AiEmbedding;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.domain.RerankerProvider;
import com.yutong.ai.rag.mapper.AiDocumentChunkMapper;
import com.yutong.ai.rag.mapper.AiDocumentMapper;
import com.yutong.ai.rag.mapper.AiEmbeddingMapper;
import com.yutong.ai.rag.mapper.AiKnowledgeBaseMapper;
import com.yutong.ai.rag.repository.VectorRepository;
import com.yutong.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 检索服务。设计来源: 13-AI能力设计 检索策略 / P1-1 混合检索 + 重排器串联
 * <p>
 * 检索流程:
 * <ol>
 *   <li>先按租户、知识库、权限范围过滤（结构化授权过滤）</li>
 *   <li>vector 召回 (pgvector cosine) —— KB 感知 embedding (远程 5s 超时降级哈希)</li>
 *   <li>如 kb.hybridEnabled 则调用 HybridRetrievalService 融合 (vector 0.7 + tsv 0.3 RRF 加权)</li>
 *   <li>如 kb.rerankerEnabled 则调用 RerankerProvider.rerank(query, chunks, topK) 重排，返回前截断</li>
 *   <li>topK=5，低置信度时提示"未找到可靠依据"</li>
 * </ol>
 * 安全约束: 用户无权访问的文档不能进入召回候选，也不能在引用来源中暴露；保持租户/DataScope 隔离。
 */
@Service
public class RagRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RagRetrievalService.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final double MIN_CONFIDENCE_SCORE = 0.01;

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final AiEmbeddingMapper embeddingMapper;
    private final RagAclService aclService;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final HybridRetrievalService hybridRetrievalService;
    private final Map<String, RerankerProvider> rerankerMap;
    private final AiProviderRegistry providerRegistry;

    public RagRetrievalService(AiKnowledgeBaseMapper kbMapper,
                               AiDocumentMapper documentMapper,
                               AiDocumentChunkMapper chunkMapper,
                               AiEmbeddingMapper embeddingMapper,
                               RagAclService aclService,
                               EmbeddingService embeddingService,
                               VectorRepository vectorRepository,
                               HybridRetrievalService hybridRetrievalService,
                               List<RerankerProvider> rerankerProviders,
                               AiProviderRegistry providerRegistry) {
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingMapper = embeddingMapper;
        this.aclService = aclService;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.hybridRetrievalService = hybridRetrievalService;
        this.providerRegistry = providerRegistry;
        // 构建 providerCode -> bean 映射 (小写)
        Map<String, RerankerProvider> map = new java.util.HashMap<>();
        if (rerankerProviders != null) {
            for (RerankerProvider rp : rerankerProviders) {
                map.put(rp.getProviderCode().toLowerCase(), rp);
            }
        }
        this.rerankerMap = map;
    }

    /**
     * 检索相关分块 — 串联 vector → hybrid(KB 可配权重) → reranker → 截断。
     */
    public List<RetrievalResult> retrieve(String tenantId, String kbId, String query,
                                          String currentUserId, int topK) {
        int limit = topK > 0 ? topK : DEFAULT_TOP_K;
        String matchQuery = query == null ? "" : query.trim();

        // 1. 校验知识库存在并校验访问权限（先做结构化授权过滤）
        AiKnowledgeBase kb = kbMapper.selectById(kbId);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库不存在: " + kbId);
        }
        aclService.checkKnowledgeBaseAccess(kb.getVisibility(), kb.getOwnerUserId(),
                kb.getPermissionCode(), currentUserId, tenantId, kb.getTenantId());

        boolean hybridEnabled = Boolean.TRUE.equals(kb.getHybridEnabled());
        boolean rerankerEnabled = Boolean.TRUE.equals(kb.getRerankerEnabled());
        String rerankerProviderCode = kb.getRerankerProvider();

        List<RetrievalResult> primaryResults;

        if (hybridEnabled) {
            // V050 P2-E: 权重/门限取 KB 可视化配置 (null 时 HybridRetrievalService 内 fallback 硬默认)
            int fetchLimit = rerankerEnabled ? limit * 3 : limit;
            try {
                List<HybridRetrievalService.HybridResult> hybrids =
                        hybridRetrievalService.hybridSearch(tenantId, kbId, matchQuery, currentUserId, fetchLimit,
                                kb.getHybridVectorWeight() == null ? null : kb.getHybridVectorWeight().doubleValue(),
                                kb.getHybridMinScore() == null ? null : kb.getHybridMinScore().doubleValue());
                primaryResults = hybrids.stream()
                        .map(hr -> new RetrievalResult(
                                hr.chunkId(),
                                hr.documentId(),
                                hr.chunkText(),
                                hr.sectionPath(),
                                hr.sourceType(),
                                hr.docTitle(),
                                hr.hybridScore(),
                                hr.permissionCode()))
                        .collect(Collectors.toList());
                log.debug("[RagRetrieval] hybrid branch kbId={}, queryLen={}, fetch={}, results={}", kbId, matchQuery.length(), fetchLimit, primaryResults.size());
            } catch (Exception e) {
                log.warn("[RagRetrieval] hybrid search failed, fallback to vector kbId={}, err={}", kbId, e.getMessage());
                primaryResults = doVectorSearch(kb, tenantId, currentUserId, matchQuery, rerankerEnabled ? limit * 3 : limit);
            }
        } else {
            primaryResults = doVectorSearch(kb, tenantId, currentUserId, matchQuery, rerankerEnabled ? limit * 3 : limit);
        }

        if (primaryResults.isEmpty()) {
            return List.of();
        }

        // 3. Reranker 重排 (如启用)
        if (rerankerEnabled && rerankerProviderCode != null && !rerankerProviderCode.isBlank()) {
            RerankerProvider rp = rerankerMap.get(rerankerProviderCode.trim().toLowerCase());
            if (rp == null) {
                log.warn("[RagRetrieval] reranker provider not found: {}, available={}", rerankerProviderCode, rerankerMap.keySet());
            } else {
                try {
                    String apiKeyRef = resolveRerankerApiKeyRef(tenantId, rerankerProviderCode);
                    List<RerankerProvider.RerankCandidate> candidates = primaryResults.stream()
                            .map(r -> new RerankerProvider.RerankCandidate(r.chunkId(), r.chunkText(), r.score()))
                            .toList();
                    RerankerProvider.RerankRequest req = new RerankerProvider.RerankRequest(matchQuery, candidates, apiKeyRef, limit);
                    List<RerankerProvider.RerankResult> reranked = rp.rerank(req);
                    if (reranked != null && !reranked.isEmpty()) {
                        Map<String, RetrievalResult> resultMap = primaryResults.stream()
                                .collect(Collectors.toMap(RetrievalResult::chunkId, r -> r, (a, b) -> a));
                        List<RetrievalResult> rerankedResults = new ArrayList<>();
                        for (RerankerProvider.RerankResult rr : reranked) {
                            RetrievalResult orig = resultMap.get(rr.chunkId());
                            if (orig == null) continue;
                            // 使用 rerankScore 覆盖原分，保留原 permission
                            rerankedResults.add(new RetrievalResult(
                                    orig.chunkId(), orig.documentId(), orig.chunkText(),
                                    orig.sectionPath(), orig.sourceType(), orig.docTitle(),
                                    rr.rerankScore(), orig.permissionCode()));
                        }
                        if (!rerankedResults.isEmpty()) {
                            primaryResults = rerankedResults;
                            log.debug("[RagRetrieval] reranked via {} candidates={}, reranked={}", rp.getProviderCode(), candidates.size(), rerankedResults.size());
                        }
                    } else {
                        log.warn("[RagRetrieval] reranker returned empty, keep primary results");
                    }
                } catch (Exception e) {
                    log.warn("[RagRetrieval] rerank failed, fallback to primary results provider={}, err={}", rerankerProviderCode, e.getMessage());
                }
            }
        }

        // 4. 返回前截断 + 低置信度过滤
        List<RetrievalResult> filtered = primaryResults.stream()
                .filter(r -> r.score() >= MIN_CONFIDENCE_SCORE)
                .toList();
        if (filtered.size() > limit) filtered = filtered.subList(0, limit);
        return filtered;
    }

    /**
     * 纯向量召回（pgvector cosine）— KB 感知 embedding，DataScope/租户隔离已在上游完成候选过滤。
     */
    private List<RetrievalResult> doVectorSearch(AiKnowledgeBase kb, String tenantId, String currentUserId,
                                                 String matchQuery, int fetchLimit) {
        int limit = fetchLimit > 0 ? fetchLimit : DEFAULT_TOP_K;
        // 查询 kb 下所有 ACTIVE 文档
        List<AiDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKbId, kb.getId())
                .eq(AiDocument::getDocumentStatus, AiDocument.STATUS_ACTIVE));
        if (documents.isEmpty()) return List.of();

        List<String> docIds = documents.stream().map(AiDocument::getId).toList();
        List<RagAclService.DocumentAclInfo> aclInfos = documents.stream()
                .map(doc -> new RagAclService.DocumentAclInfo(
                        doc.getId(), doc.getVisibility(), doc.getPermissionCode(),
                        kb.getOwnerUserId(), doc.getTenantId(), doc.getSensitivityLevel()))
                .toList();
        List<String> accessibleDocIds = aclService.filterAccessibleDocumentIds(
                docIds, aclInfos, currentUserId, tenantId);
        if (accessibleDocIds.isEmpty()) return List.of();

        List<AiDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiDocumentChunk>()
                .in(AiDocumentChunk::getDocumentId, accessibleDocIds));
        if (chunks.isEmpty()) return List.of();

        List<String> chunkIds = chunks.stream().map(AiDocumentChunk::getId).toList();
        List<AiEmbedding> embeddings = embeddingMapper.selectList(new LambdaQueryWrapper<AiEmbedding>()
                .in(AiEmbedding::getChunkId, chunkIds));
        Set<String> embeddedChunkIds = embeddings.stream().map(AiEmbedding::getChunkId).collect(Collectors.toSet());
        chunks = chunks.stream().filter(c -> embeddedChunkIds.contains(c.getId())).toList();
        if (chunks.isEmpty()) return List.of();

        List<AiDocumentChunk> candidateChunks = chunks;
        List<VectorRepository.SimilarityResult> similarityResults;

        if (matchQuery.isEmpty()) {
            similarityResults = candidateChunks.stream()
                    .map(c -> new VectorRepository.SimilarityResult(c.getId(), 0.0))
                    .toList();
        } else {
            float[] queryVector = embeddingService.embed(matchQuery, kb);
            String queryVectorPg = embeddingService.toPgVectorFormat(queryVector);
            List<String> candidateChunkIds = candidateChunks.stream().map(AiDocumentChunk::getId).toList();
            similarityResults = vectorRepository.searchByCosineSimilarity(candidateChunkIds, queryVectorPg, limit);
        }

        if (similarityResults.isEmpty()) return List.of();

        Map<String, AiDocument> docMap = documents.stream().collect(Collectors.toMap(AiDocument::getId, d -> d));
        Map<String, AiDocumentChunk> chunkMap = candidateChunks.stream().collect(Collectors.toMap(AiDocumentChunk::getId, c -> c));

        List<RetrievalResult> results = new ArrayList<>();
        for (VectorRepository.SimilarityResult sr : similarityResults) {
            AiDocumentChunk chunk = chunkMap.get(sr.chunkId());
            if (chunk == null) continue;
            AiDocument doc = docMap.get(chunk.getDocumentId());
            if (doc == null) continue;
            if (sr.score() < MIN_CONFIDENCE_SCORE) continue;
            results.add(new RetrievalResult(
                    chunk.getId(), chunk.getDocumentId(), chunk.getChunkText(),
                    chunk.getSectionPath(), doc.getSourceType(), doc.getDocTitle(),
                    sr.score(), chunk.getPermissionCode()));
        }
        if (results.size() > limit) results = results.subList(0, limit);
        return results;
    }

    private String resolveRerankerApiKeyRef(String tenantId, String providerCode) {
        if (providerCode == null || providerCode.isBlank() || providerRegistry == null) return null;
        try {
            String code = providerCode.trim().toLowerCase();
            // 优先按 providerType + modelType=reranker 精确匹配
            var list = providerRegistry.resolveForTenant(tenantId, code, "reranker");
            if (!list.isEmpty()) return list.get(0).getApiKeyRef();
            // 回退：所有 reranker 供应商中匹配 providerCode 或 providerType
            var all = providerRegistry.resolveForTenant(tenantId, null, "reranker");
            for (var p : all) {
                if (code.equalsIgnoreCase(p.getProviderCode()) || code.equalsIgnoreCase(p.getProviderType())) {
                    return p.getApiKeyRef();
                }
            }
            if (!all.isEmpty()) return all.get(0).getApiKeyRef();
        } catch (Exception e) {
            log.debug("[RagRetrieval] resolve reranker apiKeyRef failed: {}", e.getMessage());
        }
        return null;
    }

    public record RetrievalResult(String chunkId, String documentId, String chunkText,
                                  String sectionPath, String sourceType, String docTitle,
                                  double score, String permissionCode) {
    }
}
