package com.yutong.common.event;

import java.time.OffsetDateTime;

/**
 * 申请单状态变更事件。设计来源: 44-实时通信与消息推送设计 line 130-139、contracts/registries/events.yaml
 *
 * <p>由 {@code BizRequestApplicationService.doTransition()} 在状态流转成功后发布，
 * 由 system-service 中的 {@code RealtimeEventListener} 监听，调用 {@code PushService} 推送实时信封。
 *
 * <p>事件类型对齐 events.yaml:
 * <ul>
 *   <li>submit → biz.request.submitted (TODO_CREATED)</li>
 *   <li>approve → biz.request.approved (NOTIFICATION)</li>
 *   <li>reject → biz.request.rejected (NOTIFICATION)</li>
 *   <li>withdraw → biz.request.withdrawn (NOTIFICATION)</li>
 *   <li>archive → biz.request.archived (NOTIFICATION)</li>
 *   <li>create/save → biz.request.created (TODO_CREATED)</li>
 * </ul>
 *
 * <p>事件设计原则: 事件类只携带最小必要信息，监听器按需查询数据库获取详情。
 * 这样事件类稳定，业务字段扩展不需要修改事件类。
 */
public class BizRequestStateChangedEvent {

    private final String requestId;
    private final String requestNo;
    private final String fromStatus;
    private final String toStatus;
    private final String action; // SUBMIT/APPROVE/REJECT/WITHDRAW/ARCHIVE
    private final String applicantUserId;
    private final String tenantId;
    private final String traceId;
    private final OffsetDateTime occurredTime;

    public BizRequestStateChangedEvent(String requestId, String requestNo,
                                       String fromStatus, String toStatus, String action,
                                       String applicantUserId, String tenantId, String traceId) {
        this.requestId = requestId;
        this.requestNo = requestNo;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.action = action;
        this.applicantUserId = applicantUserId;
        this.tenantId = tenantId;
        this.traceId = traceId;
        this.occurredTime = OffsetDateTime.now();
    }

    public String getRequestId() { return requestId; }
    public String getRequestNo() { return requestNo; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public String getAction() { return action; }
    public String getApplicantUserId() { return applicantUserId; }
    public String getTenantId() { return tenantId; }
    public String getTraceId() { return traceId; }
    public OffsetDateTime getOccurredTime() { return occurredTime; }

    /**
     * 映射到 events.yaml 中的 eventType。对齐 44 号文档 line 132 "事件标识必须使用 events.yaml 的稳定 eventType"。
     */
    public String eventType() {
        return switch (action) {
            case "SUBMIT" -> "biz.request.submitted";
            case "APPROVE" -> "biz.request.approved";
            case "REJECT" -> "biz.request.rejected";
            case "WITHDRAW" -> "biz.request.withdrawn";
            case "ARCHIVE" -> "biz.request.archived";
            default -> "biz.request.created";
        };
    }

    /**
     * 映射到 44 号文档 subType。对齐 44 号文档 line 134-139。
     */
    public String subType() {
        return switch (action) {
            case "SUBMIT" -> "TODO_CREATED";
            case "APPROVE", "REJECT", "WITHDRAW", "ARCHIVE" -> "NOTIFICATION";
            default -> "TODO_CREATED";
        };
    }

    /**
     * 映射到 44 号文档 type。对齐 44 号文档 line 60-69。
     * SUBMIT 产生待办（推送给审批人），其他动作产生通知（推送给申请人）。
     */
    public String envelopeType() {
        return "SUBMIT".equals(action) ? "TODO" : "NOTIFICATION";
    }
}
