package com.yutong.ai.rag.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档分块服务。设计来源: 13-AI能力设计 RAG 分块策略
 * 默认 chunk 大小 800-1200 中文字符，重叠 100-200 字符。
 * 按标题层级、段落、代码块结构化分块（第一版采用固定窗口 + 重叠的简化实现）。
 */
@Service
public class RagChunkService {

    private static final int CHUNK_SIZE = 1000;
    private static final int OVERLAP = 150;

    /**
     * 将文本分块。
     * <ul>
     *   <li>空文本返回空列表</li>
     *   <li>长度 &le; CHUNK_SIZE 返回单块</li>
     *   <li>长度 &gt; CHUNK_SIZE 按 (CHUNK_SIZE - OVERLAP) 步长切分，带重叠</li>
     * </ul>
     *
     * @param text 原始文本
     * @return 分块列表
     */
    public List<String> splitIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return chunks;
        }
        if (text.length() <= CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }
        int step = CHUNK_SIZE - OVERLAP;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            chunks.add(text.substring(start, end));
            if (end >= text.length()) {
                break;
            }
            start += step;
        }
        return chunks;
    }

    /**
     * 计算分块的 hash（SHA-256），用于去重。
     *
     * @param chunkText 分块文本
     * @return SHA-256 十六进制摘要
     */
    public String computeChunkHash(String chunkText) {
        if (chunkText == null) {
            chunkText = "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(chunkText.getBytes(StandardCharsets.UTF_8));
            return toHexString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }

    private String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
