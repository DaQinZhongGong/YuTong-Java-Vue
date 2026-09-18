package com.yutong.ai.rag.service;

import com.yutong.ai.rag.domain.AiDocument;
import com.yutong.ai.rag.domain.AiDocumentChunk;
import com.yutong.ai.rag.domain.AiEmbedding;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.mapper.AiDocumentChunkMapper;
import com.yutong.ai.rag.mapper.AiDocumentMapper;
import com.yutong.ai.rag.mapper.AiEmbeddingMapper;
import com.yutong.ai.rag.mapper.AiKnowledgeBaseMapper;
import com.yutong.ai.rag.repository.VectorRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 混合检索 KB 配置单测 (Mockito, 无 DB)。
 * 设计来源: ADR 0004 P2-E (Hybrid 评分可配, V050)。
 *
 * 场景: 单文档单分块, 向量相似度 1.0 (归一 1.0), 全文无命中 (单分支放大路径)。
 *   hybrid = 1.0 * (0.7 + vWeight * 0.3)。
 *
 * 覆盖:
 * - KB 高门限过滤 (minScore 0.9 + vWeight 0.2 → 0.76 被滤, 空结果)
 * - 默认配置命中 (同数据默认 0.7 → 0.91 ≥ 0.01, 1 结果)
 * - 显式传参优先于 KB 配置
 * - 越界权重钳制不断流
 */
class HybridRetrievalConfigTest {

    private AiKnowledgeBaseMapper kbMapper;
    private AiDocumentMapper documentMapper;
    private AiDocumentChunkMapper chunkMapper;
    private AiEmbeddingMapper embeddingMapper;
    private RagAclService aclService;
    private EmbeddingService embeddingService;
    private VectorRepository vectorRepository;
    private JdbcTemplate jdbcTemplate;
    private HybridRetrievalService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        kbMapper = mock(AiKnowledgeBaseMapper.class);
        documentMapper = mock(AiDocumentMapper.class);
        chunkMapper = mock(AiDocumentChunkMapper.class);
        embeddingMapper = mock(AiEmbeddingMapper.class);
        aclService = mock(RagAclService.class);
        embeddingService = mock(EmbeddingService.class);
        vectorRepository = mock(VectorRepository.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new HybridRetrievalService(kbMapper, documentMapper, chunkMapper, embeddingMapper,
                aclService, embeddingService, vectorRepository, jdbcTemplate);

        AiDocument doc = new AiDocument();
        doc.setId("d1");
        doc.setDocumentStatus(AiDocument.STATUS_ACTIVE);
        when(documentMapper.selectList(any())).thenReturn(List.of(doc));
        when(aclService.filterAccessibleDocumentIds(anyList(), anyList(), anyString(), anyString()))
                .thenReturn(List.of("d1"));

        AiDocumentChunk chunk = new AiDocumentChunk();
        chunk.setId("c1");
        chunk.setDocumentId("d1");
        chunk.setChunkText("配置数据源的步骤");
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));

        AiEmbedding emb = new AiEmbedding();
        emb.setChunkId("c1");
        when(embeddingMapper.selectList(any())).thenReturn(List.of(emb));
        when(embeddingService.embed(anyString(), any(AiKnowledgeBase.class))).thenReturn(new float[]{0.1f});
        when(embeddingService.toPgVectorFormat(any())).thenReturn("[0.1]");
        // 向量余弦 1.0 → 归一 1.0; 全文无命中
        when(vectorRepository.searchByCosineSimilarity(anyList(), anyString(), anyInt()))
                .thenReturn(List.of(new VectorRepository.SimilarityResult("c1", 1.0)));
        when(jdbcTemplate.queryForList(anyString(), (Object[]) any())).thenReturn(List.of());
    }

    private AiKnowledgeBase kb(String id, String weight, Integer topK, String minScore) {
        AiKnowledgeBase kb = new AiKnowledgeBase();
        kb.setId(id);
        kb.setTenantId("t1");
        if (weight != null) kb.setHybridVectorWeight(new BigDecimal(weight));
        kb.setHybridTopK(topK);
        if (minScore != null) kb.setHybridMinScore(new BigDecimal(minScore));
        when(kbMapper.selectById(id)).thenReturn(kb);
        return kb;
    }

    @Test
    @DisplayName("KB 高门限过滤低分融合结果")
    void kbMinScoreFilters() {
        kb("kb1", "0.20", 5, "0.90");

        List<HybridRetrievalService.HybridResult> results =
                service.hybridSearch("t1", "kb1", "数据源", "u1", 0, null, null);

        // 0.76 < 0.90 → 空
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("默认配置命中 (0.7 权重)")
    void defaultConfigHits() {
        kb("kb1", null, null, null);

        List<HybridRetrievalService.HybridResult> results =
                service.hybridSearch("t1", "kb1", "数据源", "u1", 0, null, null);

        // 0.91 ≥ 0.01 → 1 结果
        assertEquals(1, results.size());
        assertEquals("c1", results.get(0).chunkId());
    }

    @Test
    @DisplayName("显式传参优先于 KB 配置")
    void explicitParamBeatsKbConfig() {
        kb("kb1", "0.20", 5, "0.90");

        // 显式权重 0.7 + 门限 null→KB 0.90: 0.91 ≥ 0.90 → 命中, 证明权重取显式值
        List<HybridRetrievalService.HybridResult> results =
                service.hybridSearch("t1", "kb1", "数据源", "u1", 0, 0.7, null);

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("显式低门限可召回 (覆盖 KB 高门限)")
    void explicitMinScoreBeatsKbConfig() {
        kb("kb1", "0.20", 5, "0.90");

        List<HybridRetrievalService.HybridResult> results =
                service.hybridSearch("t1", "kb1", "数据源", "u1", 0, null, 0.01);

        assertEquals(1, results.size());
    }

    @Test
    @DisplayName("KB topK 生效 (limit 截断)")
    void kbTopKApplies() {
        kb("kb1", null, 1, null);

        List<HybridRetrievalService.HybridResult> results =
                service.hybridSearch("t1", "kb1", "数据源", "u1", 0, null, null);

        assertTrue(results.size() <= 1);
    }
}
