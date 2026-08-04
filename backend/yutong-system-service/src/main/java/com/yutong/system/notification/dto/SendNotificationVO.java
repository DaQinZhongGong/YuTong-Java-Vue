package com.yutong.system.notification.dto;

import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 发送通知结果。GA2-40 实时通知 P2 落地。
 * <p>
 * 返回创建的站内信 ID + 渲染后的标题/内容 + 各渠道分发日志列表。
 */
@Data
public class SendNotificationVO {
    /** 创建的 sys_message id */
    private String messageId;
    /** 渲染后的标题 */
    private String title;
    /** 渲染后的内容 */
    private String content;
    /** 消息类型 */
    private String msgType;
    /** 接收人 */
    private String receiverId;
    /** 创建时间 */
    private OffsetDateTime createdTime;
    /** 各渠道分发日志 ID 列表 */
    private List<String> dispatchLogIds;
    /** 实时推送是否成功 (WebSocket 推送结果) */
    private Boolean realtimePushed;
    /** 移动推送是否触发 */
    private Boolean mobilePushTriggered;
}
