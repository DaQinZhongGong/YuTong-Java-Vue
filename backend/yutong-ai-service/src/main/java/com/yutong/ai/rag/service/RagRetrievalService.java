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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 检索服务。设计来源: 13-AI能力设计 检索策略
 * <p>
 * 检索流程:
 * <ol>
 *   <li>先按租户、知识库、权限范围过滤（结构化授权过滤）</li>
 *   <li>v0.9: 使用 pgvector 余弦相似度召回（ORDER BY embedding &lt;=&gt; query_vector）</li>
 *   <li>topK=5，低置信度时提示"未找到可靠依据"</li>
 * </ol>
 * 安全约束: 用户无权访问的文档不能进入召回候选，也不能在引用来源中暴露。
 */
@Service
public class RagRetrievalService {

    private static final int DEFAULT_TOP_K = 5;
    private static final double MIN_CONFIDENCE_SCORE = 0.01;

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiDocumentMapper documentMapper;
    private final AiDocumentChunkMapper chunkMapper;
    private final AiEmbeddingMapper embeddingMapper;
    private final RagAclService aclService;
    private final EmbeddingService embeddingService;
    private final VectorRepository vectorRepository;

    public RagRetrievalService(AiKnowledgeBaseMapper kbMapper,
                               AiDocumentMapper documentMapper,
                               AiDocumentChunkMapper chunkMapper,
                               AiEmbeddingMapper embeddingMapper,
                               RagAclService aclService,
                               EmbeddingService embeddingService,
                               VectorRepository vectorRepository) {
        this.kbMapper = kbMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.embeddingMapper = embeddingMapper;
        this.aclService = aclService;
        this.embeddingService = embeddingService;
        this.vectorRepository = vectorRepository;
    }

    /**
     * 检索相关分块。
     *
     * @param tenantId      当前租户
     * @param kbId          知识库 ID
     * @param query         查询文本
     * @param currentUserId 当前用户
     * @param topK          返回数量，&le;0 时取默认值 5
     * @return 检索结果列表，按相关性降序
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

        // 2. 查询 kb 下所有 ACTIVE 文档
        List<AiDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<AiDocument>()
                .eq(AiDocument::getKbId, kbId)
                .eq(AiDocument::getDocumentStatus, AiDocument.STATUS_ACTIVE));
        if (documents.isEmpty()) {
            return List.of();
        }

        // 3. ACL 过滤（按 visibility/permission/sensitivity）
        List<String> docIds = documents.stream().map(AiDocument::getId).toList();
        List<RagAclService.DocumentAclInfo> aclInfos = documents.stream()
                .map(doc -> new RagAclService.DocumentAclInfo(
                        doc.getId(), doc.getVisibility(), doc.getPermissionCode(),
                        kb.getOwnerUserId(), doc.getTenantId(), doc.getSensitivityLevel()))
                .toList();
        List<String> accessibleDocIds = aclService.filterAccessibleDocumentIds(
                docIds, aclInfos, currentUserId, tenantId);
        if (accessibleDocIds.isEmpty()) {
            return List.of();
        }

        // 4. 查询可访问文档的分块
        List<AiDocumentChunk> chunks = chunkMapper.selectList(new LambdaQueryWrapper<AiDocumentChunk>()
                .in(AiDocumentChunk::getDocumentId, accessibleDocIds));
        if (chunks.isEmpty()) {
            return List.of();
        }

        // 4.5 仅保留已生成向量的分块（有 embedding 记录），无向量的分块不可召回
        List<String> chunkIds = chunks.stream().map(AiDocumentChunk::getId).toList();
        List<AiEmbedding> embeddings = embeddingMapper.selectList(new LambdaQueryWrapper<AiEmbedding>()
                .in(AiEmbedding::getChunkId, chunkIds));
        Set<String> embeddedChunkIds = embeddings.stream()
                .map(AiEmbedding::getChunkId).collect(Collectors.toSet());
        chunks = chunks.stream().filter(c -> embeddedChunkIds.contains(c.getId())).toList();
        if (chunks.isEmpty()) {
            return List.of();
        }

        // 5. v0.9: pgvector 余弦相似度检索
        List<AiDocumentChunk> candidateChunks = chunks;
        List<VectorRepository.SimilarityResult> similarityResults;

        if (matchQuery.isEmpty()) {
            // 无查询文本时返回所有候选分块，分数为 0
            similarityResults = candidateChunks.stream()
                    .map(c -> new VectorRepository.SimilarityResult(c.getId(), 0.0))
                    .toList();
        } else {
            // 生成查询向量并通过 pgvector 检索
            float[] queryVector = embeddingService.embed(matchQuery);
            String queryVectorPg = embeddingService.toPgVectorFormat(queryVector);
            List<String> candidateChunkIds = candidateChunks.stream()
                    .map(AiDocumentChunk::getId).toList();
            similarityResults = vectorRepository.searchByCosineSimilarity(
                    candidateChunkIds, queryVectorPg, limit);
        }

        if (similarityResults.isEmpty()) {
            return List.of();
        }

        // 6. 构建文档和分块查找表，组装结果
        Map<String, AiDocument> docMap = documents.stream()
                .collect(Collectors.toMap(AiDocument::getId, d -> d));
        Map<String, AiDocumentChunk> chunkMap = candidateChunks.stream()
                .collect(Collectors.toMap(AiDocumentChunk::getId, c -> c));

        List<RetrievalResult> results = new ArrayList<>();
        for (VectorRepository.SimilarityResult sr : similarityResults) {
            AiDocumentChunk chunk = chunkMap.get(sr.chunkId());
            if (chunk == null) {
                continue;
            }
            AiDocument doc = docMap.get(chunk.getDocumentId());
            if (doc == null) {
                continue;
            }
            // 低置信度过滤
            if (sr.score() < MIN_CONFIDENCE_SCORE) {
                continue;
            }
            results.add(new RetrievalResult(
                    chunk.getId(),
                    chunk.getDocumentId(),
                    chunk.getChunkText(),
                    chunk.getSectionPath(),
                    doc.getSourceType(),
                    doc.getDocTitle(),
                    sr.score(),
                    chunk.getPermissionCode()));
        }

        if (results.isEmpty()) {
            return List.of();
        }
        // similarityResults 已按相似度降序，保持顺序
        if (results.size() > limit) {
            results = results.subList(0, limit);
        }
        return results;
    }

    /**
     * 检索结果。
     *
     * @param chunkId        分块 ID
     * @param documentId     文档 ID
     * @param chunkText      分块文本
     * @param sectionPath    章节路径
     * @param sourceType     源类型
     * @param docTitle       文档标题
     * @param score          相关性分数
     * @param permissionCode 权限码
     */
    public record RetrievalResult(String chunkId, String documentId, String chunkText,
                                  String sectionPath, String sourceType, String docTitle,
                                  double score, String permissionCode) {
    }
}
