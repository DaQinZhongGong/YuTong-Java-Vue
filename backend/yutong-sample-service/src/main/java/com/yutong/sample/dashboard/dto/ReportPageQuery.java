package com.yutong.sample.dashboard.dto;

/**
 * 报表分页查询参数。设计来源: 42-报表与大屏可视化设计。
 */
public record ReportPageQuery(
        String keyword,
        String status,
        String reportType
) {
}
