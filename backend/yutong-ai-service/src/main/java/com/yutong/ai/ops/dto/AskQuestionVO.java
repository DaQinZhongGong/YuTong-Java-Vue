package com.yutong.ai.ops.dto;

import java.util.List;

/**
 * 知识库问答响应。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 * 包含答案、引用来源、命中统计、是否拒答等信息。
 */
public record AskQuestionVO(
    String conversationNo,
    String question,
    String answer,
    boolean refused,
    String refuseReason,
    int hitChunkCount,
    double maxScore,
    double minScore,
    double avgScore,
    long latencyMs,
    List<Citation> citations
) {
    public record Citation(
        String docId,
        String docTitle,
        String chunkId,
        String sectionPath,
        String sourceType,
        double score,
        String chunkTextPreview  // 截断到 200 字符的预览
    ) {}
}
