package com.yutong.ai.governance.dto;

import lombok.Data;

/**
 * AI 治理监控统计。设计来源: 37-AI治理与评测设计
 * <p>聚合 5 大能力域关键指标, 用于前端运营看板。
 */
@Data
public class AiGovernanceStatsVO {

    // ===== Prompt 治理 =====
    private Long totalPrompts;
    private Long publishedPrompts;
    private Long draftPrompts;

    // ===== AI 工具注册 =====
    private Long totalTools;
    private Long enabledTools;
    private Long forbiddenTools;

    // ===== 成本治理 (今日累计, 来自 ai_cost_log) =====
    private Long todayTotalTokens;
    private java.math.BigDecimal todayTotalCost;
    private Long todayCallCount;

    // ===== 反馈闭环 =====
    private Long totalFeedbacks;
    private Long helpfulFeedbacks;
    private Long riskyFeedbacks;
    private Long unhandledFeedbacks;

    // ===== RAG 评测 =====
    private Long totalEvalCases;
    private Long enabledEvalCases;
    private Long totalEvalRuns;
    private Long passedEvalRuns;
    private Long failedEvalRuns;
    /** 最近一次评测批次的发布结论: PASSED / CONDITIONAL / REJECTED / PENDING */
    private String latestRunDecision;
    private String latestRunNo;
}
