package com.yutong.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.ai.rag.domain.AiDocument;
import com.yutong.ai.rag.domain.AiDocumentChunk;
import com.yutong.ai.rag.domain.AiEmbedding;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.mapper.AiDocumentChunkMapper;
import com.yutong.ai.rag.mapper.AiDocumentMapper;
import com.yutong.ai.rag.mapper.AiEmbeddingMapper;
import com.yutong.ai.rag.mapper.AiKnowledgeBaseMapper;
import com.yutong.ai.rag.repository.VectorRepository;
import com.yutong.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 混合检索服务 — pgvector 余弦相似度 + PostgreSQL 全文检索 (tsvector) 加权融合 (RRF 变体)。
 * 设计来源: 13-AI能力设计 RAG 混合检索 / V037 content_tsv GIN 索引 / ars20260711.md 混合检索
 * <p>
 * 策略:
 * <ul>
 *   <li>先走结构化授权过滤 (与 RagRetrievalService 一致)</li>
 *   <li>向量分支: pgvector cosine (score = 1 - distance, 已归一化)</li>
 *   <li>全文分支: to_tsvector('simple', chunk_text) @@ plainto_tsquery('simple', query) + ts_rank 评分</li>
 *   <li>加权: hybridScore = vectorWeight * vectorScore + (1 - vectorWeight) * textScore，默认 0.7/0.3 (RRF 语义: 双分数归一后加权融合)</li>
 *   <li>任一分支无命中时由另一分支兜底；双分支均命中时去重合并</li>
 * </ul>
 * 约束: 权重/数量/门限三元组按“显式传参 > KB 可视化配置 > 硬默认(0.7/5/0.01)”逐级 fallback (V050 P2-E)；全文失败不阻断向量结果。
 */
@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_VECTOR_WEIGHT = 0.7;
    /** V050 P2-E: 默认准入门限 (KB 未配置时 fallback) */
    static final double DEFAULT_MIN_SCORE = 0.01;

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final AiEmbeddingMapper embeddingMapper;
    private final RagAclService aclService;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;
    private final JdbcTemplate jdbcTemplate;

    public HybridRetrievalService(AiKnowledgeBaseMapper kbMapper,
                                  AiDocumentMapper documentMapper,
                                  AiDocumentChunkMapper chunkMapper,
                                  AiEmbeddingMapper embeddingMapper,
                                  RagAclService aclService,
                                  EmbeddingService embeddingService,
                                  VectorRepository vectorRepository,
                                  JdbcTemplate jdbcTemplate) {
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingMapper = embeddingMapper;
        this.aclService = aclService;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 混合检索。
     *
     * @param tenantId      租户
     * @param kbId          知识库
     * @param query         查询文本
     * @param currentUserId 当前用户
     * @param topK          返回数量，<=0 取默认 5
     * @param vectorWeight  向量权重 0~1，null 时取默认 0.6
     * @return 按 hybridScore 降序的检索结果
     */
    public List<HybridResult> hybridSearch(String tenantId, String kbId, String query,
                                           String currentUserId, int topK, Double vectorWeight) {
        return hybridSearch(tenantId, kbId, query, currentUserId, topK, vectorWeight, null);
    }

    /**
     * 混合检索 (全参)。
     *
     * @param tenantId      租户
     * @param kbId          知识库
     * @param query         查询文本
     * @param currentUserId 当前用户
     * @param topK          返回数量, {@code <=0} 时取 KB 配置 (再 fallback 默认 5)
     * @param vectorWeight  向量权重 0~1, {@code null} 时取 KB 配置 (再 fallback 默认 0.7)
     * @param minScore      准入门限 0~1, {@code null} 时取 KB 配置 (再 fallback 默认 0.01)
     */
    public List<HybridResult> hybridSearch(String tenantId, String kbId, String query,
                                           String currentUserId, int topK, Double vectorWeight, Double minScore) {
        AiKnowledgeBase kbPre = kbMapper.selectById(kbId);
        if (kbPre == null) {
            throw new ResourceNotFoundException("知识库不存在: " + kbId);
        }
        // V050 P2-E: 显式传参优先, 否则取 KB 可视化配置, 再 fallback 硬默认
        int limit = topK > 0 ? topK
                : (kbPre.getHybridTopK() != null && kbPre.getHybridTopK() > 0 ? kbPre.getHybridTopK() : DEFAULT_TOP_K);
        double vWeight = vectorWeight != null ? clamp(vectorWeight)
                : (kbPre.getHybridVectorWeight() != null ? clamp(kbPre.getHybridVectorWeight().doubleValue()) : DEFAULT_VECTOR_WEIGHT);
        double floor = minScore != null ? clamp(minScore)
                : (kbPre.getHybridMinScore() != null ? clamp(kbPre.getHybridMinScore().doubleValue()) : DEFAULT_MIN_SCORE);
        double tWeight = 1.0 - vWeight;
        String matchQuery = query == null ? "" : query.trim();

        AiKnowledgeBase kb = kbPre;
        aclService.checkKnowledgeBaseAccess(kb.getVisibility(), kb.getOwnerUserId(),
                kb.getPermissionCode(), currentUserId, tenantId, kb.getTenantId());

        List<AiDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKbId, kbId)
                .eq(AiDocument::getDocumentStatus, AiDocument.STATUS_ACTIVE));
        if (documents.isEmpty()) {
            return List.of();
        }

        List<String> docIds = documents.stream().map(AiDocument::getId).toList();
        List<RagAclService.DocumentAclInfo> aclInfos = documents.stream()
                .map(doc -> new RagAclService.DocumentAclInfo(
                        doc.getId(), doc.getVisibility(), doc.getPermissionCode(),
                        kb.getOwnerUserId(), doc.getTenantId(), doc.getSensitivityLevel()))
                .toList();
        List<String> accessibleDocIds = aclService.filterAccessibleDocumentIds(docIds, aclInfos, currentUserId, tenantId);
        if (accessibleDocIds.isEmpty()) {
            return List.of();
        }

        List<AiDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiDocumentChunk>()
                .in(AiDocumentChunk::getDocumentId, accessibleDocIds));
        if (chunks.isEmpty()) {
            return List.of();
        }

        // 仅保留已向量化的分块 (向量分支需要)，但全文分支可检索未向量化分块；此处取并集，hybrid 合并时各自过滤
        Map<String, AiDocumentChunk> chunkMap = chunks.stream()
                .collect(Collectors.toMap(AiDocumentChunk::getId, c -> c));

        List<String> chunkIds = chunks.stream().map(AiDocumentChunk::getId).toList();
        Map<String, Double> vectorScores = fetchVectorScores(chunkIds, matchQuery, limit * 2, kb);
        Map<String, Double> textScores = fetchTextScores(chunkIds, matchQuery);

        // 合并: 并集去重，加权
        Set<String> allIds = new java.util.HashSet<>();
        allIds.addAll(vectorScores.keySet());
        allIds.addAll(textScores.keySet());

        if (allIds.isEmpty()) {
            // 无查询文本时返回按向量兜底的空结果
            if (matchQuery.isEmpty()) {
                return chunks.stream().limit(limit)
                        .map(c -> toHybridResult(c, documents, chunkMap, 0, 0, 0))
                        .filter(r -> r != null)
                        .toList();
            }
            return List.of();
        }

        Map<String, AiDocument> docMap = documents.stream()
                .collect(Collectors.toMap(AiDocument::getId, d -> d));

        List<HybridResult> results = new ArrayList<>();
        for (String chunkId : allIds) {
            double vScore = vectorScores.getOrDefault(chunkId, 0.0);
            double tScore = textScores.getOrDefault(chunkId, 0.0);
            double hybrid = vScore * vWeight + tScore * tWeight;
            // 若仅单分支有分，适度放大有效分支避免被权重稀释
            if (vectorScores.containsKey(chunkId) && !textScores.containsKey(chunkId)) {
                hybrid = vScore * (0.7 + vWeight * 0.3);
            } else if (!vectorScores.containsKey(chunkId) && textScores.containsKey(chunkId)) {
                hybrid = tScore * (0.7 + tWeight * 0.3);
            }
            if (hybrid < floor) continue;
            AiDocumentChunk chunk = chunkMap.get(chunkId);
            if (chunk == null) continue;
            AiDocument doc = docMap.get(chunk.getDocumentId());
            if (doc == null) continue;
            results.add(new HybridResult(
                    chunk.getId(), chunk.getDocumentId(), chunk.getChunkText(),
                    chunk.getSectionPath(), doc.getSourceType(), doc.getDocTitle(),
                    hybrid, vScore, tScore, chunk.getPermissionCode()));
        }

        results.sort(Comparator.comparingDouble(HybridResult::hybridScore).reversed());
        if (results.size() > limit) results = results.subList(0, limit);

        log.debug("[HybridRetrieval] kbId={}, queryLen={}, candidates={}, vectorHits={}, textHits={}, results={}, vWeight={}",
                kbId, matchQuery.length(), chunkIds.size(), vectorScores.size(), textScores.size(), results.size(), vWeight);
        return results;
    }

    /** 便捷重载 (默认权重 0.7) */
    public List<HybridResult> hybridSearch(String tenantId, String kbId, String query,
                                           String currentUserId, int topK) {
        return hybridSearch(tenantId, kbId, query, currentUserId, topK, null);
    }

    @SuppressWarnings("unused")
    private Map<String, Double> fetchVectorScores(List<String> chunkIds, String query, int fetchLimit) {
        return fetchVectorScores(chunkIds, query, fetchLimit, null);
    }

    private Map<String, Double> fetchVectorScores(List<String> chunkIds, String query, int fetchLimit, AiKnowledgeBase kb) {
        if (query.isEmpty()) return Map.of();
        // 仅对已嵌入 chunk 查询向量
        List<String> embeddedIds = embeddingMapper.selectList(new LambdaQueryWrapper<AiEmbedding>()
                        .in(AiEmbedding::getChunkId, chunkIds))
                .stream().map(AiEmbedding::getChunkId).toList();
        if (embeddedIds.isEmpty()) return Map.of();
        try {
            float[] qv = kb != null ? embeddingService.embed(query, kb) : embeddingService.embed(query);
            String qvPg = embeddingService.toPgVectorFormat(qv);
            List<VectorRepository.SimilarityResult> sims = vectorRepository.searchByCosineSimilarity(embeddedIds, qvPg, fetchLimit);
            Map<String, Double> map = new HashMap<>();
            for (VectorRepository.SimilarityResult s : sims) {
                // 将余弦相似度从 [-1,1] 归一到 [0,1]
                double norm = (s.score() + 1) / 2.0;
                map.put(s.chunkId(), clamp(norm));
            }
            return map;
        } catch (Exception e) {
            log.warn("[HybridRetrieval] vector search failed, fallback to text only", e);
            return Map.of();
        }
    }

    private Map<String, Double> fetchTextScores(List<String> chunkIds, String query) {
        if (query == null || query.isBlank() || chunkIds.isEmpty()) return Map.of();
        String sanitized = sanitizeTsQuery(query);
        if (sanitized.isBlank()) return Map.of();
        try {
            String placeholders = String.join(",", java.util.Collections.nCopies(chunkIds.size(), "?"));
            String sql = """
                    SELECT id AS chunk_id,
                           ts_rank(content_tsv, plainto_tsquery('simple', ?)) AS tscore
                    FROM ai_document_chunk
                    WHERE id IN (%s)
                      AND deleted = false
                      AND content_tsv @@ plainto_tsquery('simple', ?)
                    ORDER BY tscore DESC
                    """.formatted(placeholders);
            List<Object> params = new ArrayList<>();
            params.add(sanitized);
            params.addAll(chunkIds);
            params.add(sanitized);
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params.toArray());
            Map<String, Double> map = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String cid = (String) row.get("chunk_id");
                Object scoreObj = row.get("tscore");
                double score = scoreObj instanceof Number n ? n.doubleValue() : 0;
                map.put(cid, clamp(score));
            }
            return map;
        } catch (Exception e) {
            log.warn("[HybridRetrieval] fulltext search failed, fallback to vector only", e);
            return Map.of();
        }
    }

    private HybridResult toHybridResult(AiDocumentChunk chunk, List<AiDocument> documents,
                                        Map<String, AiDocumentChunk> chunkMap,
                                        double hybrid, double vScore, double tScore) {
        // helper for empty query branch
        Map<String, AiDocument> docMap = documents.stream().collect(Collectors.toMap(AiDocument::getId, d -> d));
        AiDocument doc = docMap.get(chunk.getDocumentId());
        if (doc == null) return null;
        return new HybridResult(chunk.getId(), chunk.getDocumentId(), chunk.getChunkText(),
                chunk.getSectionPath(), doc.getSourceType(), doc.getDocTitle(), hybrid, vScore, tScore, chunk.getPermissionCode());
    }

    private String sanitizeTsQuery(String query) {
        // 移除 tsvector 特殊字符，保留中英文数字空格
        String s = query.replaceAll("[&|!:'()]", " ").trim();
        if (s.length() > 400) s = s.substring(0, 400);
        return s;
    }

    private double clamp(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }

    /**
     * 混合检索结果 — 含向量/全文/混合三分数，便于前端展示与阈值过滤。
     *
     * @param chunkId       分块 ID
     * @param documentId    文档 ID
     * @param chunkText     分块文本
     * @param sectionPath   章节路径
     * @param sourceType    源类型
     * @param docTitle      文档标题
     * @param hybridScore   混合分数 (加权)
     * @param vectorScore   向量分数 [0,1]
     * @param textScore     全文分数 [0,1]
     * @param permissionCode 权限码
     */
    public record HybridResult(String chunkId, String documentId, String chunkText,
                               String sectionPath, String sourceType, String docTitle,
                               double hybridScore, double vectorScore, double textScore,
                               String permissionCode) {
    }
}
