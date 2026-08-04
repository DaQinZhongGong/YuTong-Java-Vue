package com.yutong.ai.rag.dto;

/**
 * RAG 检索请求体。设计来源: 13-AI能力设计 检索策略
 *
 * @param kbId  知识库 ID
 * @param query 查询文本
 * @param topK  返回数量，可选，默认 5
 */
public record RagRetrievalRequest(
        String kbId,
        String query,
        Integer topK
) {
}
