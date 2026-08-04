package com.yutong.workflow.dto;

import lombok.Data;

/**
 * 工作流运营监控统计 VO。用于运营监控看板展示。
 */
@Data
public class WorkflowStatsVO {

    /** 流程定义总数 (PUBLISHED 状态) */
    private long publishedDefinitionCount;

    /** 流程实例总数 (含历史) */
    private long totalInstanceCount;

    /** 运行中实例数 (RUNNING 状态) */
    private long runningInstanceCount;

    /** 已完成实例数 (COMPLETED 状态) */
    private long completedInstanceCount;

    /** 已终止实例数 (TERMINATED 状态) */
    private long terminatedInstanceCount;

    /** 待办任务总数 (PENDING 状态) */
    private long pendingTaskCount;

    /** 已办任务总数 (COMPLETED/REJECTED/DELEGATED/TRANSFERRED/CANCELLED 状态) */
    private long completedTaskCount;

    /** 当前用户待办数 */
    private long myPendingTaskCount;

    /** 当前用户已办数 */
    private long myCompletedTaskCount;
}
