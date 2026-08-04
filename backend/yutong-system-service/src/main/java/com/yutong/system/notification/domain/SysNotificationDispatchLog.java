package com.yutong.system.notification.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 通知分发日志。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知 + 44-实时通信与消息推送设计
 * <p>
 * GA2-40 落地: 一条站内信可能分发到多渠道 (IN_APP/MOBILE_PUSH/SMS/EMAIL)，每次分发独立记录用于重试和审计。
 * 重试机制: 失败后 status=RETRYING，next_retry_time = now + retry_backoff_ms * 2^retry_count。
 * 死信队列: retry_count >= max_retry_count 时进入 DEAD_LETTER 终态。
 */
@Getter
@Setter
@TableName("sys_notification_dispatch_log")
public class SysNotificationDispatchLog extends BaseEntity {
    /** 关联 sys_message id */
    private String messageId;
    /** 接收人 user_id */
    private String receiverId;
    /** 分发渠道: IN_APP / MOBILE_PUSH / SMS / EMAIL */
    private String channel;
    /** 分发状态: PENDING / SENT / FAILED / RETRYING / DEAD_LETTER */
    private String dispatchStatus;
    /** 重试次数 (从 0 开始, 每次失败 +1) */
    private Integer retryCount;
    /** 最大重试次数 */
    private Integer maxRetryCount;
    /** 退避基数 (毫秒) */
    private Long retryBackoffMs;
    /** 下次重试时间 */
    private OffsetDateTime nextRetryTime;
    /** 最近一次错误信息 */
    private String lastError;
    /** 实际发送时间 */
    private OffsetDateTime sentTime;
    /** 投递载荷快照 (JSON) */
    private String payloadSnapshot;

    /** channel 常量 */
    public static final String CHANNEL_IN_APP = "IN_APP";
    public static final String CHANNEL_MOBILE_PUSH = "MOBILE_PUSH";
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_EMAIL = "EMAIL";

    /** dispatch_status 常量 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_RETRYING = "RETRYING";
    public static final String STATUS_DEAD_LETTER = "DEAD_LETTER";

    /** 默认重试参数 */
    public static final int DEFAULT_MAX_RETRY = 3;
    public static final long DEFAULT_BACKOFF_MS = 1000L;
}
