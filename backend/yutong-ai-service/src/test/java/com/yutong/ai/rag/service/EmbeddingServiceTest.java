package com.yutong.ai.rag.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * EmbeddingService 单元测试。
 * v0.9: 验证本地哈希词袋向量的确定性、维度、归一化、相似度区分度。
 */
class EmbeddingServiceTest {

    private final EmbeddingService service = new EmbeddingService();

    @Test
    void embed_shouldReturnCorrectDimension() {
        float[] vector = service.embed("hello world");
        assertEquals(1536, vector.length, "向量维度必须为 1536");
    }

    @Test
    void embed_shouldBeDeterministic() {
        float[] v1 = service.embed("相同的文本");
        float[] v2 = service.embed("相同的文本");
        assertArrayEquals(v1, v2, 0.0001f, "相同文本必须产生相同向量");
    }

    @Test
    void embed_nullOrBlank_shouldReturnZeroVector() {
        float[] nullVec = service.embed(null);
        float[] emptyVec = service.embed("");
        float[] blankVec = service.embed("   ");
        assertEquals(1536, nullVec.length);
        assertEquals(1536, emptyVec.length);
        assertEquals(1536, blankVec.length);
        for (int i = 0; i < 1536; i++) {
            assertEquals(0.0f, nullVec[i], "空文本向量应为零");
            assertEquals(0.0f, emptyVec[i]);
            assertEquals(0.0f, blankVec[i]);
        }
    }

    @Test
    void embed_shouldBeL2Normalized() {
        float[] vector = service.embed("这是一个测试文本用于验证L2归一化");
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        assertEquals(1.0f, norm, 0.001f, "非空文本向量 L2 范数应接近 1");
    }

    @Test
    void embed_similarTextsShouldHaveHigherSimilarityThanDissimilar() {
        float[] v1 = service.embed("客户管理 客户列表 客户信息");
        float[] v2 = service.embed("客户管理 客户详情 客户信息");
        float[] v3 = service.embed("数据库迁移 服务器配置 系统参数");

        double sim12 = cosineSimilarity(v1, v2);
        double sim13 = cosineSimilarity(v1, v3);

        assertTrue(sim12 > sim13,
                "相似文本的余弦相似度应高于不相似文本: sim12=" + sim12 + " sim13=" + sim13);
        assertTrue(sim12 > 0.1, "共享多词的文本相似度应 > 0.1: " + sim12);
    }

    @Test
    void embed_sameTextSimilarityShouldBeOne() {
        float[] v = service.embed("完全相同的文本");
        double selfSim = cosineSimilarity(v, v);
        assertEquals(1.0, selfSim, 0.001, "自身余弦相似度应为 1");
    }

    @Test
    void toPgVectorFormat_shouldBeValidFormat() {
        float[] vector = service.embed("test");
        String pgString = service.toPgVectorFormat(vector);
        assertNotNull(pgString);
        assertTrue(pgString.startsWith("["), "pgvector 格式应以 [ 开头");
        assertTrue(pgString.endsWith("]"), "pgvector 格式应以 ] 结尾");
        // 验证逗号分隔
        long commaCount = pgString.chars().filter(c -> c == ',').count();
        assertEquals(1535, commaCount, "1536 维向量应有 1535 个逗号分隔符");
    }

    @Test
    void embed_chineseTokenizationShouldWork() {
        float[] v1 = service.embed("客户管理系统");
        float[] v2 = service.embed("客户");
        double sim = cosineSimilarity(v1, v2);
        assertTrue(sim > 0, "中文分词后共享'客户'应产生正相似度: " + sim);
    }

    @Test
    void embed_englishTokenizationShouldWork() {
        float[] v1 = service.embed("customer management system");
        float[] v2 = service.embed("customer");
        double sim = cosineSimilarity(v1, v2);
        assertTrue(sim > 0, "英文分词后共享 'customer' 应产生正相似度: " + sim);
    }

    /**
     * 计算两个向量的余弦相似度。
     */
    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
