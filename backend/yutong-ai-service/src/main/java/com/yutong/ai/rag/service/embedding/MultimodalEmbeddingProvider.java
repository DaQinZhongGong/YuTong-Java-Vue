package com.yutong.ai.rag.service.embedding;

import com.yutong.ai.gateway.domain.AiProvider;

/**
 * 多模态 Embedding 供应商抽象 — 图像/视频向量化。
 * 设计来源: docs/compose/spec/ai-depth-parity.md S2.3
 * 约束: 失败关闭（无 Key / 远端异常直接抛出，不静默降级假向量）；不落明文密钥。
 */
public interface MultimodalEmbeddingProvider extends RemoteEmbeddingProvider {

    /**
     * 是否支持该模态。
     *
     * @param modality text | image | video | audio
     */
    boolean supportsModality(String modality);

    /**
     * 将图像向量化。payload 为 http(s) URL 或 data:image/...;base64,...。
     *
     * @throws Exception 无 Key 或远端失败时抛出（fail-closed）
     */
    float[] embedImage(String imageUrlOrDataUrl, String model, AiProvider provider) throws Exception;

    /**
     * 将视频向量化。payload 为可访问的视频 URL。
     *
     * @throws Exception 无 Key 或远端失败时抛出（fail-closed）
     */
    float[] embedVideo(String videoUrl, String model, AiProvider provider) throws Exception;
}
