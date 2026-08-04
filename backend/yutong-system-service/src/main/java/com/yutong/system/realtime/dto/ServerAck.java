package com.yutong.system.realtime.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 服务端 → 客户端的 ACK 响应。设计来源: 44-实时通信与消息推送设计
 *
 * <p>对客户端 AUTH/PING/SUBSCRIBE/ACK_* 指令的同步回执，便于客户端确认状态。
 * 业务消息推送使用 {@link RealtimeEnvelope}，不使用本类。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerAck {

    public static final String STATUS_OK = "OK";
    public static final String STATUS_ERROR = "ERROR";

    private String action;
    private String status;
    private String message;
    private String errorCode;

    public ServerAck() {
    }

    public ServerAck(String action, String status, String message) {
        this.action = action;
        this.status = status;
        this.message = message;
    }

    public static ServerAck ok(String action, String message) {
        return new ServerAck(action, STATUS_OK, message);
    }

    public static ServerAck error(String action, String errorCode, String message) {
        ServerAck ack = new ServerAck(action, STATUS_ERROR, message);
        ack.errorCode = errorCode;
        return ack;
    }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
}
