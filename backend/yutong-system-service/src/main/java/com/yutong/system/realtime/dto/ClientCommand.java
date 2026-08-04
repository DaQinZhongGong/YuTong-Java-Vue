package com.yutong.system.realtime.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 客户端 → 服务端 指令信封。设计来源: 44-实时通信与消息推送设计 line 89-99
 *
 * <p>action 枚举:
 * <ul>
 *   <li>AUTH: 首帧鉴权（生产推荐，token 不入 URL）</li>
 *   <li>PING: 心跳</li>
 *   <li>SUBSCRIBE: 订阅频道（服务端校验权限，禁止跨租户）</li>
 *   <li>ACK_DELIVERED: 确认送达（投递去重）</li>
 *   <li>ACK_READ: 确认已读（联动 sys_message.read_status）</li>
 *   <li>ACK_HANDLED: 业务处理完成</li>
 *   <li>ACK_FAILED: 客户端处理失败，携带 errorCode</li>
 * </ul>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClientCommand {

    public static final String ACTION_AUTH = "AUTH";
    public static final String ACTION_PING = "PING";
    public static final String ACTION_SUBSCRIBE = "SUBSCRIBE";
    public static final String ACTION_ACK_DELIVERED = "ACK_DELIVERED";
    public static final String ACTION_ACK_READ = "ACK_READ";
    public static final String ACTION_ACK_HANDLED = "ACK_HANDLED";
    public static final String ACTION_ACK_FAILED = "ACK_FAILED";

    private String action;
    private String token;
    /** 订阅频道，如 user:{userId} 或 tenant:{tenantId}。服务端必须校验归属。 */
    private String channel;
    /** ACK 类指令携带的 messageId，用于幂等去重。 */
    private String messageId;
    /** ACK_FAILED 携带的错误码，供服务端统计。 */
    private String errorCode;

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
