package com.yutong.system.notification.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 移动端订阅。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知 + 44-实时通信与消息推送设计
 * <p>
 * GA2-40 落地: 用户按 topic + channel 维度订阅推送，支持开启/关闭。
 * topic 对齐 SysMessageTemplate.msg_type，channel 限定为 MOBILE_PUSH/SMS/EMAIL。
 */
@Getter
@Setter
@TableName("sys_notification_subscription")
public class SysNotificationSubscription extends BaseEntity {
    /** 订阅用户 */
    private String userId;
    /** 订阅主题 (对齐 msg_type) */
    private String topic;
    /** 推送渠道: MOBILE_PUSH / SMS / EMAIL */
    private String channel;
    /** 是否启用 */
    private Boolean enabled;
    /** 设备 token */
    private String deviceToken;
    /** 备注 */
    private String remark;

    /** channel 常量 (与 SysNotificationDispatchLog 对齐) */
    public static final String CHANNEL_MOBILE_PUSH = "MOBILE_PUSH";
    public static final String CHANNEL_SMS = "SMS";
    public static final String CHANNEL_EMAIL = "EMAIL";
}
