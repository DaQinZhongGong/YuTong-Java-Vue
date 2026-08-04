package com.yutong.system.notification.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.id.IdGenerator;
import com.yutong.system.message.domain.SysMessage;
import com.yutong.system.message.domain.SysTodoTask;
import com.yutong.system.message.mapper.SysMessageMapper;
import com.yutong.system.message.mapper.SysTodoTaskMapper;
import com.yutong.system.notification.domain.SysNotificationDispatchLog;
import com.yutong.system.notification.mapper.SysNotificationDispatchLogMapper;
import com.yutong.system.realtime.dto.RealtimeEnvelope;
import com.yutong.system.realtime.service.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * SLA 超时通知定时任务。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知、44-实时通信与消息推送设计。
 *
 * <p>每 30 分钟扫描一次 {@code sys_todo_task} 中处于 PENDING 且已超过 SLA 的待办
 * （待办覆盖 biz_request 审批/办理等业务类型，由 {@code biz_type} 区分），
 * 对每条超时待办执行:
 * <ol>
 *   <li>幂等判定: 提醒窗口内已发过超时站内信则跳过，避免每 30 分钟重复轰炸</li>
 *   <li>写入站内信 {@code sys_message} (msg_type=SYSTEM, read_status=UNREAD)</li>
 *   <li>WebSocket 实时推送 (PERSIST_THEN_PUSH: 先落库再推送，推送失败客户端可 REST 拉取补偿)</li>
 *   <li>写入分发日志 {@code sys_notification_dispatch_log}，按推送结果记 SENT / FAILED
 *       并回填 last_error 与 next_retry_time，供通知重试与审计使用</li>
 * </ol>
 *
 * <p>超时判定口径: 待办有 {@code due_time} 时以 due_time 为准；无 due_time 时按
 * {@code created_time + slaTimeoutHours} 兜底。
 *
 * <p>可调参数 (application.yml):
 * <ul>
 *   <li>{@code yutong.notification.sla.timeout-hours} 默认 24</li>
 *   <li>{@code yutong.notification.sla.remind-interval-hours} 默认 24 (同一待办的重复提醒间隔)</li>
 *   <li>{@code yutong.notification.sla.batch-limit} 默认 500 (单次扫描处理上限，防止长事务与雪崩)</li>
 * </ul>
 */
@Component
public class SlaTimeoutNotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaTimeoutNotificationScheduler.class);

    /** 待办状态: 待处理 */
    private static final String TODO_STATUS_PENDING = "PENDING";
    /** 站内信类型 */
    private static final String MSG_TYPE_SYSTEM = "SYSTEM";
    /** 站内信未读状态 */
    private static final String READ_STATUS_UNREAD = "UNREAD";
    /** 推送 subType (events.yaml 事件类型) */
    private static final String SUB_TYPE_SLA_TIMEOUT = "todo.sla.timeout";
    /** 站内信跳转路由 */
    private static final String TARGET_ROUTE_TODOS = "/workbench/todos";

    private final SysTodoTaskMapper todoTaskMapper;
    private final SysMessageMapper messageMapper;
    private final SysNotificationDispatchLogMapper dispatchLogMapper;
    private final PushService pushService;
    private final ObjectMapper objectMapper;

    private final long slaTimeoutHours;
    private final long remindIntervalHours;
    private final int batchLimit;

    public SlaTimeoutNotificationScheduler(SysTodoTaskMapper todoTaskMapper,
                                           SysMessageMapper messageMapper,
                                           SysNotificationDispatchLogMapper dispatchLogMapper,
                                           PushService pushService,
                                           ObjectMapper objectMapper,
                                           @Value("${yutong.notification.sla.timeout-hours:24}") long slaTimeoutHours,
                                           @Value("${yutong.notification.sla.remind-interval-hours:24}") long remindIntervalHours,
                                           @Value("${yutong.notification.sla.batch-limit:500}") int batchLimit) {
        this.todoTaskMapper = todoTaskMapper;
        this.messageMapper = messageMapper;
        this.dispatchLogMapper = dispatchLogMapper;
        this.pushService = pushService;
        this.objectMapper = objectMapper;
        this.slaTimeoutHours = slaTimeoutHours;
        this.remindIntervalHours = remindIntervalHours;
        this.batchLimit = batchLimit;
    }

    /**
     * 每 30 分钟扫描 SLA 超时待办。
     * cron: 0 0/30 * * * *（每 30 分钟执行，@EnableScheduling 已在 YutongApplication 启用）
     */
    @Scheduled(cron = "0 0/30 * * * *")
    public void scanOverdueTodos() {
        OffsetDateTime now = OffsetDateTime.now();
        List<SysTodoTask> overdueList = findOverdueTodos(now);
        if (overdueList.isEmpty()) {
            log.debug("[SLA超时通知] 无超时待办");
            return;
        }
        log.info("[SLA超时通知] 发现 {} 条超时待办 (阈值 {} 小时)", overdueList.size(), slaTimeoutHours);

        int notified = 0;
        int skipped = 0;
        int failed = 0;
        for (SysTodoTask todo : overdueList) {
            try {
                if (todo.getAssigneeId() == null || todo.getAssigneeId().isBlank()) {
                    // 无办理人的待办无法定向通知，交由待办分派流程补齐 assignee_id
                    log.warn("[SLA超时通知] 待办缺少办理人，跳过: todoId={}, bizType={}, bizId={}",
                            todo.getId(), todo.getBizType(), todo.getBizId());
                    skipped++;
                    continue;
                }
                if (alreadyNotified(todo, now)) {
                    skipped++;
                    continue;
                }
                notifyOverdue(todo, now);
                notified++;
            } catch (Exception e) {
                failed++;
                log.warn("[SLA超时通知] 发送通知失败 todoId={} - {}", todo.getId(), e.getMessage());
            }
        }
        log.info("[SLA超时通知] 处理完成: 通知 {} 条, 跳过 {} 条, 失败 {} 条", notified, skipped, failed);
    }

    /**
     * 查询超时待办: todo_status=PENDING 且 (due_time < now 或 due_time 为空且 created_time < now - slaTimeoutHours)。
     * 按到期时间升序，单次最多处理 batchLimit 条。
     */
    private List<SysTodoTask> findOverdueTodos(OffsetDateTime now) {
        OffsetDateTime fallbackDeadline = now.minusHours(slaTimeoutHours);
        LambdaQueryWrapper<SysTodoTask> wrapper = new LambdaQueryWrapper<SysTodoTask>()
                .eq(SysTodoTask::getTodoStatus, TODO_STATUS_PENDING)
                .and(w -> w
                        .lt(SysTodoTask::getDueTime, now)
                        .or(sub -> sub
                                .isNull(SysTodoTask::getDueTime)
                                .lt(SysTodoTask::getCreatedTime, fallbackDeadline)))
                .orderByAsc(SysTodoTask::getCreatedTime)
                .last("LIMIT " + Math.max(1, batchLimit));
        return todoTaskMapper.selectList(wrapper);
    }

    /**
     * 幂等判定: 提醒窗口内该待办是否已发过超时站内信。
     * 以 (tenant_id, receiver_id, biz_type, biz_id, msg_type) + created_time 窗口为去重键，
     * 避免每轮扫描对同一条待办重复写站内信。
     */
    private boolean alreadyNotified(SysTodoTask todo, OffsetDateTime now) {
        OffsetDateTime remindAfter = now.minusHours(remindIntervalHours);
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, todo.getTenantId())
                .eq(SysMessage::getReceiverId, todo.getAssigneeId())
                .eq(SysMessage::getMsgType, MSG_TYPE_SYSTEM)
                .eq(SysMessage::getBizType, todo.getBizType())
                .eq(todo.getBizId() != null, SysMessage::getBizId, todo.getBizId())
                .isNull(todo.getBizId() == null, SysMessage::getBizId)
                .ge(SysMessage::getCreatedTime, remindAfter);
        Long count = messageMapper.selectCount(wrapper);
        boolean notified = count != null && count > 0;
        if (notified) {
            log.debug("[SLA超时通知] 提醒窗口内已通知过，跳过: todoId={}", todo.getId());
        }
        return notified;
    }

    /** 写站内信 → 实时推送 → 按推送结果写分发日志。 */
    private void notifyOverdue(SysTodoTask todo, OffsetDateTime now) {
        String title = "待办已超时: " + (todo.getTitle() != null ? todo.getTitle() : todo.getBizType());
        String content = buildContent(todo, now);

        // 1. 写站内信 (PERSIST_THEN_PUSH: 先落库，推送失败也可由 REST 拉取补偿)
        SysMessage message = new SysMessage();
        message.setId(IdGenerator.nextId());
        message.setTenantId(todo.getTenantId());
        message.setReceiverId(todo.getAssigneeId());
        message.setMsgType(MSG_TYPE_SYSTEM);
        message.setTitle(title);
        message.setContent(content);
        message.setReadStatus(READ_STATUS_UNREAD);
        message.setBizType(todo.getBizType());
        message.setBizId(todo.getBizId());
        message.setTargetRouteId(TARGET_ROUTE_TODOS);
        message.setTargetParams(toJson(Map.of(
                "todoId", nullToEmpty(todo.getId()),
                "bizType", nullToEmpty(todo.getBizType()),
                "bizId", nullToEmpty(todo.getBizId()))));
        messageMapper.insert(message);

        // 2. WebSocket 实时推送
        String pushError = pushRealtime(todo, message, title, content);

        // 3. 写分发日志 (按推送结果落 SENT / FAILED)
        dispatchLogMapper.insert(buildDispatchLog(todo, message, title, pushError, now));
    }

    /** 实时推送，返回 null 表示成功，否则返回错误摘要。 */
    private String pushRealtime(SysTodoTask todo, SysMessage message, String title, String content) {
        try {
            RealtimeEnvelope envelope = new RealtimeEnvelope(
                    todo.getAssigneeId(), todo.getTenantId(),
                    RealtimeEnvelope.TYPE_TODO, SUB_TYPE_SLA_TIMEOUT,
                    title, content, todo.getBizType(), todo.getBizId(),
                    null);
            envelope.setMessageId(message.getId());
            envelope.setPersisted(true);
            envelope.setPriority(todo.getPriority() != null ? todo.getPriority() : "HIGH");
            envelope.setDeliveryMode(RealtimeEnvelope.DELIVERY_PERSIST_THEN_PUSH);
            pushService.push(envelope);
            return null;
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            log.warn("[SLA超时通知] 实时推送失败 todoId={} - {}", todo.getId(), msg);
            return msg;
        }
    }

    /** 构造分发日志: 推送失败时状态为 FAILED 并预置下次重试时间，供通知重试链路接管。 */
    private SysNotificationDispatchLog buildDispatchLog(SysTodoTask todo, SysMessage message,
                                                        String title, String pushError, OffsetDateTime now) {
        SysNotificationDispatchLog logEntry = new SysNotificationDispatchLog();
        logEntry.setId(IdGenerator.nextId());
        logEntry.setTenantId(todo.getTenantId());
        logEntry.setMessageId(message.getId());
        logEntry.setReceiverId(todo.getAssigneeId());
        logEntry.setChannel(SysNotificationDispatchLog.CHANNEL_IN_APP);
        logEntry.setRetryCount(0);
        logEntry.setMaxRetryCount(SysNotificationDispatchLog.DEFAULT_MAX_RETRY);
        logEntry.setRetryBackoffMs(SysNotificationDispatchLog.DEFAULT_BACKOFF_MS);
        logEntry.setPayloadSnapshot(toJson(Map.of(
                "channel", SysNotificationDispatchLog.CHANNEL_IN_APP,
                "subType", SUB_TYPE_SLA_TIMEOUT,
                "title", title,
                "todoId", nullToEmpty(todo.getId()))));
        if (pushError == null) {
            logEntry.setDispatchStatus(SysNotificationDispatchLog.STATUS_SENT);
            logEntry.setSentTime(now);
        } else {
            logEntry.setDispatchStatus(SysNotificationDispatchLog.STATUS_FAILED);
            logEntry.setLastError(truncate(pushError, 512));
            logEntry.setNextRetryTime(now.plusNanos(SysNotificationDispatchLog.DEFAULT_BACKOFF_MS * 1_000_000L));
        }
        return logEntry;
    }

    private String buildContent(SysTodoTask todo, OffsetDateTime now) {
        StringBuilder sb = new StringBuilder("您有一条待办任务已超时，请及时办理。");
        if (todo.getBizType() != null) {
            sb.append(" 业务类型: ").append(todo.getBizType()).append('.');
        }
        if (todo.getDueTime() != null) {
            sb.append(" 到期时间: ").append(todo.getDueTime()).append('.');
        } else {
            sb.append(" 已超过 ").append(slaTimeoutHours).append(" 小时未处理.");
        }
        if (todo.getCreatedTime() != null) {
            long hours = java.time.Duration.between(todo.getCreatedTime(), now).toHours();
            sb.append(" 待办创建至今 ").append(hours).append(" 小时.");
        }
        return sb.toString();
    }

    /** 序列化为 JSON 字符串 (jsonb 列)，失败时降级为 null 不阻断通知主流程。 */
    private String toJson(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(payload));
        } catch (JsonProcessingException e) {
            log.warn("[SLA超时通知] JSON 序列化失败 - {}", e.getMessage());
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
