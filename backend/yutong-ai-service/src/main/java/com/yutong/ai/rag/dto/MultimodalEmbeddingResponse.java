package com.yutong.ai.rag.dto;

import java.util.List;

/**
 * 多模态 Embedding 测试端点响应 — 不返回完整向量，仅维度 + 前 8 维预览 + 是否远程成功。
 * 设计来源: docs/compose/spec/ai-depth-parity.md S2.3
 *
 * @param modality      请求模态
 * @param dimension     向量维度
 * @param remoteSuccess 是否远程供应商成功（false = 哈希兜底或失败说明）
 * @param preview       前 8 维预览（不足 8 维则全量）
 * @param message       补充信息 / 错误原因
 */
public record MultimodalEmbeddingResponse(
        String modality,
        Integer dimension,
        Boolean remoteSuccess,
        List<Float> preview,
        String message
) {
}
