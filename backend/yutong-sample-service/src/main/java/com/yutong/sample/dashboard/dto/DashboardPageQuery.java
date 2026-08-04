package com.yutong.sample.dashboard.dto;

/**
 * 大屏分页查询参数。设计来源: 42-报表与大屏可视化设计。
 */
public record DashboardPageQuery(
        String keyword,
        String status,
        String theme
) {
}
