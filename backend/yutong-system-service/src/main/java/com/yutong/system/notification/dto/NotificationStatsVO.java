package com.yutong.system.notification.dto;

import lombok.Data;

/**
 * 通知运营监控统计。GA2-40 实时通知 P2 落地。
 * <p>
 * 用于前端"运营监控"Tab 展示 8 项核心指标。
 */
@Data
public class NotificationStatsVO {
    /** 模板总数 */
    private Long templateCount;
    /** 启用模板数 */
    private Long enabledTemplateCount;
    /** 今日发送站内信数 */
    private Long todayMessageCount;
    /** 今日已读数 */
    private Long todayReadCount;
    /** 今日未读数 */
    private Long todayUnreadCount;
    /** 今日分发日志总数 */
    private Long todayDispatchCount;
    /** 今日失败分发数 */
    private Long todayFailedDispatchCount;
    /** 待重试分发数 (status=RETRYING 或 FAILED 且 next_retry_time 已到) */
    private Long pendingRetryCount;
    /** 死信队列数 (status=DEAD_LETTER) */
    private Long deadLetterCount;
    /** 当前用户未读数 (实时推送用) */
    private Long currentUserUnreadCount;
    /** 移动端订阅数 */
    private Long subscriptionCount;
}
