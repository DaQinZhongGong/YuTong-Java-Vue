package com.yutong.ai.ops.dto;

/**
 * 知识库运营统计。设计来源: 35-样例业务矩阵扩展设计 P2 知识库运营。
 * 5 项核心指标: 文档数/分块数/今日问答/命中率/平均分。
 */
public record KbStatsVO(
    String kbId,
    String kbName,
    String kbStatus,
    long documentCount,
    long chunkCount,
    long embeddingCount,
    long todayConversationCount,
    long todayHitCount,
    long todayRefusedCount,
    double todayHitRate,        // 命中率 = hit / conversation
    Double todayAvgMaxScore,    // 可能为 null (无问答时)
    Long todayAvgLatencyMs,
    long totalConversationCount,
    long totalHitCount,
    long totalRefusedCount
) {}
