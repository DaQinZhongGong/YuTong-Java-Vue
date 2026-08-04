package com.yutong.sample.workbench.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 工作台趋势数据 VO。设计来源: 42-报表与大屏可视化设计 R0 验收标准（基础 ECharts 图表）。
 * <p>
 * 对齐 42 号文档内置数据集 {@code biz_request_trend_7d}（近 7 日提交趋势，SQL 按日）。
 * 前端折线图 xField=date, yField=count/amount。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchTrendVO {

    /** 趋势数据点列表，按日期升序。 */
    private List<TrendPoint> points;

    /** 合计提交数（points 求和，便于卡片展示）。 */
    private long totalCount;

    /** 合计金额（points 求和）。 */
    private BigDecimal totalAmount;

    /**
     * 单日趋势数据点。
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrendPoint {
        /** 日期，格式 yyyy-MM-dd */
        private String date;
        /** 当日提交申请单数 */
        private long count;
        /** 当日提交申请单金额合计 */
        private BigDecimal amount;
    }
}
