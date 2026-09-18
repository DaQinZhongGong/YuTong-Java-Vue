package com.yutong.ai.governance.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 用量日报 VO (日趋势查询结果)。
 * 设计来源: P2-C Token 用量可视化 (M-2) — 基于 ai_cost_quota_usage 日累计行。
 */
@Getter
@Setter
public class AiUsageDailyVO {

    /** 明细行 (按日期升序, 同日按模型排序) */
    private List<Row> rows;

    /** 区间 token 总量 */
    private long totalTokens;

    /** 区间成本总额 (CNY) */
    private BigDecimal totalCost;

    /** 实际查询起日 */
    private LocalDate startDate;

    /** 实际查询止日 */
    private LocalDate endDate;

    @Getter
    @Setter
    public static class Row {
        private LocalDate usageDate;
        private String quotaScope;
        private String scopeKey;
        private String modelCode;
        private Long tokenUsed;
        private BigDecimal costUsed;
    }
}
