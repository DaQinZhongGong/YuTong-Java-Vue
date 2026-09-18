package com.yutong.ai.rag.domain;

import java.util.List;

/**
 * Reranker 重排供应商抽象。
 * 设计来源: ars20260711.md 9_reranker.md / 13-AI能力设计 RAG 重排
 * 约束: mock 实现返回确定性分数，不落明文密钥，api_key_ref 由 DB 配置注入。
 */
public interface RerankerProvider {

    /**
     * 供应商编码，与 ai_knowledge_base.reranker_provider 一致。
     * 可选值: alibailian / siliconflow / zhipu
     */
    String getProviderCode();

    /**
     * 对候选分块进行重排。
     *
     * @param request 重排请求 (query + candidates + apiKeyRef + topN)
     * @return 按 rerankScore 降序的重排结果，数量不超过 topN
     */
    List<RerankResult> rerank(RerankRequest request);

    // ===== 内嵌 DTO =====

    /**
     * 重排候选 — 来自初排 (Hybrid / Vector) 的候选。
     *
     * @param chunkId       分块 ID
     * @param chunkText     分块文本
     * @param originalScore 初排分数
     */
    record RerankCandidate(String chunkId, String chunkText, double originalScore) {
    }

    /**
     * 重排请求。
     *
     * @param query      查询文本
     * @param candidates 候选列表
     * @param apiKeyRef  密钥引用 (来自 ai_provider.api_key_ref，不落明文)
     * @param topN       返回数量
     */
    record RerankRequest(String query, List<RerankCandidate> candidates, String apiKeyRef, int topN) {
    }

    /**
     * 重排结果。
     *
     * @param chunkId       分块 ID
     * @param rerankScore   重排分数 (0~1，越高越相关)
     * @param originalScore 初排分数
     */
    record RerankResult(String chunkId, double rerankScore, double originalScore) {
    }
}
