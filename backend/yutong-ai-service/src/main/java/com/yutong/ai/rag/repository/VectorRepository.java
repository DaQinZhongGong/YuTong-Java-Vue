package com.yutong.ai.rag.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

/**
 * pgvector 向量仓储。设计来源: 13-AI能力设计 RAG 向量检索。
 * <p>
 * v0.9 落地: 使用 JdbcTemplate 执行原生 SQL 操作 pgvector 的 vector(1536) 列，
 * 绕过 MyBatis-Plus 不支持 vector 类型的限制。
 * <ul>
 *   <li>{@link #insertVector} — 向量写入（INSERT ... embedding = ?::vector）</li>
 *   <li>{@link #updateVector} — 向量更新</li>
 *   <li>{@link #searchByCosineSimilarity} — 余弦相似度检索（ORDER BY embedding &lt;=&gt; ?::vector）</li>
 * </ul>
 */
@Repository
public class VectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public VectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 写入向量到 ai_embedding 表。
     *
     * @param embeddingId     embedding 记录 ID
     * @param vectorPgString  pgvector 格式字符串，如 "[0.1,0.2,...]"
     */
    public void insertVector(String embeddingId, String vectorPgString) {
        String sql = "UPDATE ai_embedding SET embedding = ?::vector WHERE id = ? AND deleted = false";
        jdbcTemplate.update(sql, vectorPgString, embeddingId);
    }

    /**
     * 按余弦相似度检索最相关的分块。
     * <p>
     * pgvector 的 &lt;=&gt; 操作符返回余弦距离（0=完全相同, 2=完全相反），
     * score = 1 - distance 即余弦相似度。
     *
     * @param chunkIds       候选分块 ID 列表
     * @param queryVector    查询向量的 pgvector 格式字符串
     * @param topK           返回数量
     * @return 检索结果列表，按相似度降序
     */
    public List<SimilarityResult> searchByCosineSimilarity(List<String> chunkIds, String queryVector, int topK) {
        if (chunkIds == null || chunkIds.isEmpty()) {
            return List.of();
        }
        // 构建 IN 参数占位符
        String placeholders = String.join(",", java.util.Collections.nCopies(chunkIds.size(), "?"));
        String sql = """
                SELECT e.chunk_id,
                       (1 - (e.embedding <=> ?::vector)) AS score
                FROM ai_embedding e
                WHERE e.chunk_id IN (%s)
                  AND e.deleted = false
                  AND e.embedding IS NOT NULL
                ORDER BY e.embedding <=> ?::vector
                LIMIT ?
                """.formatted(placeholders);

        List<Object> params = new ArrayList<>();
        params.add(queryVector);       // SELECT 中的 score 计算
        params.addAll(chunkIds);       // IN 条件
        params.add(queryVector);       // ORDER BY
        params.add(topK);              // LIMIT

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            String chunkId = rs.getString("chunk_id");
            double score = rs.getDouble("score");
            return new SimilarityResult(chunkId, score);
        }, params.toArray());
    }

    /**
     * 相似度检索结果。
     *
     * @param chunkId 分块 ID
     * @param score   余弦相似度（-1 ~ 1，越接近 1 越相似）
     */
    public record SimilarityResult(String chunkId, double score) {
    }
}
