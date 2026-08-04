package com.yutong.system.realtime.service;

import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.event.BizRequestStateChangedEvent;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.realtime.dto.RealtimeEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 实时推送事件监听器。设计来源: 44-实时通信与消息推送设计 line 130-139、contracts/registries/events.yaml
 *
 * <p>监听 {@link BizRequestStateChangedEvent}，调用 {@link PushService} 推送实时信封到申请人/审批人。
 *
 * <p>使用 @TransactionalEventListener(AFTER_COMMIT) 确保事务提交后才推送，避免回滚时误推送。
 * 推送失败不影响主事务（消息持久化留 v1.1+，当前仅推送；客户端可通过 REST 拉取补偿，44 号文档 line 7/170）。
 *
 * <p>事件 → 推送信封映射（对齐 44 号文档 line 134-139）:
 * <ul>
 *   <li>SUBMIT → TODO_CREATED → 推送给申请人（"您已提交申请单"）</li>
 *   <li>APPROVE → NOTIFICATION → 推送给申请人（"申请单已审核通过"）</li>
 *   <li>REJECT → NOTIFICATION → 推送给申请人（"申请单已驳回"）</li>
 *   <li>WITHDRAW → NOTIFICATION → 推送给申请人（"申请单已撤回"）</li>
 *   <li>ARCHIVE → NOTIFICATION → 推送给申请人（"申请单已归档"）</li>
 * </ul>
 *
 * <p>注意: 当前实现推送给 applicantUserId（申请人）。SUBMIT 场景实际应推送给审批人，
 * 但审批人列表需要查询角色/权限映射，留 v1.1+ 接入工作流后实现。
 * 现阶段申请人能看到自己提交的实时反馈，演示价值充分。
 */
@Component
public class RealtimeEventListener {

    private static final Logger log = LoggerFactory.getLogger(RealtimeEventListener.class);

    private final PushService pushService;

    public RealtimeEventListener(PushService pushService) {
        this.pushService = pushService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBizRequestStateChanged(BizRequestStateChangedEvent event) {
        if (event.getApplicantUserId() == null || event.getApplicantUserId().isBlank()) {
            log.debug("申请单状态变更事件无 applicantUserId，跳过推送 requestId={} action={}",
                    event.getRequestId(), event.getAction());
            return;
        }
        // 构造推送信封（PUSH_ONLY 模式，不持久化；持久化站内信留 v1.1+）
        String title = buildTitle(event);
        String content = buildContent(event);
        RealtimeEnvelope envelope = new RealtimeEnvelope(
                event.getApplicantUserId(),
                event.getTenantId(),
                event.envelopeType(),
                event.subType(),
                title,
                content,
                "biz_request",
                event.getRequestId(),
                event.getTraceId()
        );
        // 显式设置 subType 为 events.yaml 的 eventType（44 号文档 line 132 硬约束）
        envelope.setSubType(event.eventType());
        pushService.push(envelope);
        log.info("申请单状态变更推送 requestId={} action={} eventType={} receiver={}",
                event.getRequestId(), event.getAction(), event.eventType(), event.getApplicantUserId());
    }

    private String buildTitle(BizRequestStateChangedEvent event) {
        return switch (event.getAction()) {
            case "SUBMIT" -> "申请单已提交";
            case "APPROVE" -> "申请单已审核通过";
            case "REJECT" -> "申请单已驳回";
            case "WITHDRAW" -> "申请单已撤回";
            case "ARCHIVE" -> "申请单已归档";
            default -> "申请单状态变更";
        };
    }

    private String buildContent(BizRequestStateChangedEvent event) {
        return String.format("申请单 %s 状态由 %s 变更为 %s",
                event.getRequestNo(), event.getFromStatus(), event.getToStatus());
    }
}
