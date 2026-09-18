package com.yutong.ai.rag.dto;

/**
 * 多模态 Embedding 测试端点请求体。
 * 设计来源: docs/compose/spec/ai-depth-parity.md S2.3
 *
 * @param modality        text | image | video
 * @param payload         文本 / 图像 URL 或 data URL / 视频 URL
 * @param knowledgeBaseId 可选知识库 ID（用于 embedding 维度与模型选型）
 */
public record MultimodalEmbeddingRequest(
        String modality,
        String payload,
        String knowledgeBaseId
) {
}
