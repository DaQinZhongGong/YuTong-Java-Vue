package com.yutong.sample.extsync.dto;

import lombok.Data;

/**
 * 同步监控统计 VO。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>用于同步监控看板: 系统总数/任务总数/近期记录统计/错误队列统计。
 */
@Data
public class ExtSyncStatsVO {

    /** 外部系统总数 */
    private Long systemCount;
    /** 同步任务总数 */
    private Long taskCount;
    /** 活跃任务数 */
    private Long activeTaskCount;
    /** 同步记录总数 */
    private Long recordCount;
    /** 近 24 小时同步次数 */
    private Long recentRecordCount;
    /** 近 24 小时成功次数 */
    private Long recentSuccessCount;
    /** 近 24 小时失败次数 */
    private Long recentFailedCount;
    /** 未解决错误数 (PENDING + RETRYING) */
    private Long pendingErrorCount;
    /** 死信错误数 */
    private Long deadLetterCount;
}
