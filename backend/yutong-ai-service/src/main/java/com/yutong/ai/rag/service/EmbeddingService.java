package com.yutong.ai.rag.service;

import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;

/**
 * 本地向量化服务。设计来源: 13-AI能力设计 RAG 向量检索。
 * <p>
 * v0.9 落地: 使用确定性哈希词袋模型生成 1536 维向量，无需调用外部 embedding API。
 * 原理: 对文本分词后，每个词哈希到 [0, dimension) 区间的某个维度并计数，
 * 最后 L2 归一化。相似文本因共享词项而余弦相似度较高。
 * <p>
 * 当接入真实 embedding API（如 text-embedding-ada-002）时，只需替换本类实现，
 * 上层 VectorRepository 和 RagRetrievalService 无需改动。
 */
@Service
public class EmbeddingService {

    private static final int DIMENSION = 1536;

    /**
     * 将文本转换为 1536 维 L2 归一化的 float 向量。
     *
     * @param text 输入文本，null 或空文本返回零向量
     * @return 长度恒为 1536 的 float 数组
     */
    public float[] embed(String text) {
        float[] vector = new float[DIMENSION];
        if (text == null || text.isBlank()) {
            return vector;
        }
        // 分词: 中文按字、英文按词，统一小写
        String[] tokens = tokenize(text);
        for (String token : tokens) {
            if (token.isEmpty()) {
                continue;
            }
            int hash = stableHash(token);
            int index = Math.abs(hash % DIMENSION);
            // 符号哈希: 让不同词分散到正负方向，增强区分度
            vector[index] += (hash >= 0) ? 1.0f : -1.0f;
        }
        // L2 归一化
        float norm = 0;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < DIMENSION; i++) {
                vector[i] /= norm;
            }
        }
        return vector;
    }

    /**
     * 将 float 向量转换为 pgvector 文本格式: "[0.1,0.2,...]"
     */
    public String toPgVectorFormat(float[] vector) {
        StringBuilder sb = new StringBuilder(DIMENSION * 8);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    public int getDimension() {
        return DIMENSION;
    }

    /**
     * 分词: 中文按单字、英文按单词（非字母数字分割）。
     */
    private String[] tokenize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        // 用非字母数字下划线分割，适配中英文混合
        String[] words = lower.split("[^a-z0-9\u4e00-\u9fa5]+");
        // 中文单字拆分: 对包含中文的 token 进一步按字拆分
        var result = new java.util.ArrayList<String>();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            boolean hasCJK = false;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (c >= '\u4e00' && c <= '\u9fa5') {
                    result.add(String.valueOf(c));
                    hasCJK = true;
                }
            }
            if (!hasCJK) {
                result.add(word);
            }
        }
        return result.toArray(new String[0]);
    }

    /**
     * 稳定字符串哈希（跨平台一致），非 Java hashCode（后者依赖 JVM 实现）。
     */
    private int stableHash(String s) {
        int hash = 17;
        for (int i = 0; i < s.length(); i++) {
            hash = hash * 31 + s.charAt(i);
        }
        return hash;
    }
}
