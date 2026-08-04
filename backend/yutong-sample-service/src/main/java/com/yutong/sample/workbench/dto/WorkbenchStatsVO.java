package com.yutong.sample.workbench.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作台统计数据 VO。设计来源: 18-样例业务详细设计 工作台统计、19-页面原型与站点地图。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchStatsVO {
    private long totalCustomers;
    private long totalProducts;
    private long totalRequests;
    private long draftRequests;
    private long submittedRequests;
    private long approvedRequests;
    private long rejectedRequests;
    private long archivedRequests;
    private long pendingTodos;
    private long unreadMessages;
}
