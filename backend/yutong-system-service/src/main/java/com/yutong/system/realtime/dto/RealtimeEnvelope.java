package com.yutong.system.realtime.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yutong.common.id.IdGenerator;

import java.time.OffsetDateTime;

/**
 * 统一推送信封。设计来源: 44-实时通信与消息推送设计 line 39-58
 *
 * <p>WebSocket / SSE 共用同一信封结构，对齐 events.yaml envelope 字段。
 * 关键字段:
 * <ul>
 *   <li>messageId: ULID，客户端 ACK 去重用</li>
 *   <li>channel: user:{userId} 或 tenant:{tenantId}，服务端从登录上下文生成</li>
 *   <li>type: NOTIFICATION / TODO / TASK_PROGRESS / SYSTEM / WORKFLOW / PING / BATCH</li>
 *   <li>deliveryMode: PERSIST_THEN_PUSH（先写 sys_message 再推送）/ PUSH_ONLY（仅推送）</li>
 *   <li>persisted: 是否已落库（true 表示站内信已写入，客户端可拉取补偿）</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RealtimeEnvelope {

    /** type 枚举常量，对齐 44 号文档 line 60-69 */
    public static final String TYPE_NOTIFICATION = "NOTIFICATION";
    public static final String TYPE_TODO = "TODO";
    public static final String TYPE_TASK_PROGRESS = "TASK_PROGRESS";
    public static final String TYPE_SYSTEM = "SYSTEM";
    public static final String TYPE_WORKFLOW = "WORKFLOW";
    public static final String TYPE_PING = "PING";
    public static final String TYPE_BATCH = "BATCH";

    /** deliveryMode 枚举，对齐 44 号文档 line 53 */
    public static final String DELIVERY_PERSIST_THEN_PUSH = "PERSIST_THEN_PUSH";
    public static final String DELIVERY_PUSH_ONLY = "PUSH_ONLY";

    private String messageId;
    private String tenantId;
    private String receiverUserId;
    private String channel;
    private String type;
    private String subType;
    private String title;
    private String content;
    private String bizType;
    private String bizId;
    private String priority;
    private Boolean persisted;
    private String deliveryMode;
    private OffsetDateTime expireAt;
    private OffsetDateTime timestamp;
    private String traceId;

    public RealtimeEnvelope() {
    }

    /**
     * 快速构造推送信封（PUSH_ONLY 模式，无持久化）。
     *
     * @param receiverUserId 接收用户 ID
     * @param tenantId       租户 ID
     * @param type           消息类型（TYPE_* 常量）
     * @param subType        子类型（events.yaml 的 eventType，如 biz.request.submitted）
     * @param title          标题
     * @param content        内容
     * @param bizType        业务类型（如 biz_request）
     * @param bizId          业务 ID
     * @param traceId        链路 ID
     */
    public RealtimeEnvelope(String receiverUserId, String tenantId, String type, String subType,
                            String title, String content, String bizType, String bizId, String traceId) {
        this.messageId = IdGenerator.nextId();
        this.tenantId = tenantId;
        this.receiverUserId = receiverUserId;
        this.channel = "user:" + receiverUserId;
        this.type = type;
        this.subType = subType;
        this.title = title;
        this.content = content;
        this.bizType = bizType;
        this.bizId = bizId;
        this.priority = "NORMAL";
        this.persisted = false;
        this.deliveryMode = DELIVERY_PUSH_ONLY;
        this.timestamp = OffsetDateTime.now();
        this.traceId = traceId;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getTenantId() { return tenantId; }
    public void setTenantId(String tenantId) { this.tenantId = tenantId; }

    public String getReceiverUserId() { return receiverUserId; }
    public void setReceiverUserId(String receiverUserId) { this.receiverUserId = receiverUserId; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSubType() { return subType; }
    public void setSubType(String subType) { this.subType = subType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getBizType() { return bizType; }
    public void setBizType(String bizType) { this.bizType = bizType; }

    public String getBizId() { return bizId; }
    public void setBizId(String bizId) { this.bizId = bizId; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Boolean getPersisted() { return persisted; }
    public void setPersisted(Boolean persisted) { this.persisted = persisted; }

    public String getDeliveryMode() { return deliveryMode; }
    public void setDeliveryMode(String deliveryMode) { this.deliveryMode = deliveryMode; }

    public OffsetDateTime getExpireAt() { return expireAt; }
    public void setExpireAt(OffsetDateTime expireAt) { this.expireAt = expireAt; }

    public OffsetDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(OffsetDateTime timestamp) { this.timestamp = timestamp; }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
}
