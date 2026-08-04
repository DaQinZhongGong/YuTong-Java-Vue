package com.yutong.system.notification.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.system.message.domain.SysMessage;
import com.yutong.system.message.domain.SysTodoTask;
import com.yutong.system.message.mapper.SysMessageMapper;
import com.yutong.system.message.mapper.SysTodoTaskMapper;
import com.yutong.system.notification.domain.SysNotificationDispatchLog;
import com.yutong.system.notification.mapper.SysNotificationDispatchLogMapper;
import com.yutong.system.realtime.dto.RealtimeEnvelope;
import com.yutong.system.realtime.service.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SlaTimeoutNotificationScheduler 单元测试。
 * 设计来源: 35-样例业务矩阵扩展设计 P2 实时通知、44-实时通信与消息推送设计。
 *
 * <p>覆盖场景:
 * <ol>
 *   <li>超时待办 → 写站内信 + 分发日志(SENT) + WebSocket 推送</li>
 *   <li>提醒窗口内已通知 → 幂等跳过，不重复写站内信</li>
 *   <li>无办理人 → 跳过，避免 receiver_id 为空的脏数据</li>
 *   <li>推送异常 → 站内信仍落库，分发日志记 FAILED + last_error + next_retry_time</li>
 *   <li>无超时待办 → 不产生任何写入</li>
 *   <li>单条失败不阻断后续待办处理</li>
 * </ol>
 */
@DisplayName("SlaTimeoutNotificationScheduler: SLA 超时待办通知")
class SlaTimeoutNotificationSchedulerTest {

    private static final String TENANT = "default";

    private SysTodoTaskMapper todoTaskMapper;
    private SysMessageMapper messageMapper;
    private SysNotificationDispatchLogMapper dispatchLogMapper;
    private PushService pushService;
    private SlaTimeoutNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        todoTaskMapper = mock(SysTodoTaskMapper.class);
        messageMapper = mock(SysMessageMapper.class);
        dispatchLogMapper = mock(SysNotificationDispatchLogMapper.class);
        pushService = mock(PushService.class);
        scheduler = new SlaTimeoutNotificationScheduler(
                todoTaskMapper, messageMapper, dispatchLogMapper, pushService,
                new ObjectMapper(), 24L, 24L, 500);
    }

    private SysTodoTask overdueTodo(String id, String assigneeId) {
        SysTodoTask todo = new SysTodoTask();
        todo.setId(id);
        todo.setTenantId(TENANT);
        todo.setTodoType("APPROVAL");
        todo.setBizType("biz_request");
        todo.setBizId("REQ" + id);
        todo.setTitle("请假申请单待审批");
        todo.setAssigneeId(assigneeId);
        todo.setTodoStatus("PENDING");
        todo.setPriority("HIGH");
        todo.setDueTime(OffsetDateTime.now().minusHours(3));
        todo.setCreatedTime(OffsetDateTime.now().minusHours(30));
        return todo;
    }

    @SuppressWarnings("unchecked")
    private void stubOverdue(List<SysTodoTask> todos) {
        when(todoTaskMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(todos);
    }

    @SuppressWarnings("unchecked")
    private void stubAlreadyNotifiedCount(long count) {
        when(messageMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(count);
    }

    @Test
    @DisplayName("超时待办: 写站内信 + 分发日志(SENT) + WebSocket 推送")
    void shouldNotifyOverdueTodo() {
        stubOverdue(List.of(overdueTodo("TODO001", "user-approver")));
        stubAlreadyNotifiedCount(0L);

        scheduler.scanOverdueTodos();

        ArgumentCaptor<SysMessage> msgCaptor = ArgumentCaptor.forClass(SysMessage.class);
        verify(messageMapper).insert(msgCaptor.capture());
        SysMessage message = msgCaptor.getValue();
        assertEquals("user-approver", message.getReceiverId());
        assertEquals(TENANT, message.getTenantId());
        assertEquals("SYSTEM", message.getMsgType());
        assertEquals("UNREAD", message.getReadStatus());
        assertEquals("biz_request", message.getBizType());
        assertEquals("REQTODO001", message.getBizId());
        assertTrue(message.getTitle().contains("待办已超时"), "标题应标识超时: " + message.getTitle());
        assertNotNull(message.getTargetParams(), "targetParams 应写入 todoId 等跳转参数");
        assertTrue(message.getTargetParams().contains("TODO001"));

        ArgumentCaptor<RealtimeEnvelope> pushCaptor = ArgumentCaptor.forClass(RealtimeEnvelope.class);
        verify(pushService).push(pushCaptor.capture());
        RealtimeEnvelope envelope = pushCaptor.getValue();
        assertEquals("user-approver", envelope.getReceiverUserId());
        assertEquals(RealtimeEnvelope.TYPE_TODO, envelope.getType());
        assertEquals(RealtimeEnvelope.DELIVERY_PERSIST_THEN_PUSH, envelope.getDeliveryMode());
        assertEquals(message.getId(), envelope.getMessageId(), "推送 messageId 应与站内信一致，便于客户端去重");
        assertEquals(Boolean.TRUE, envelope.getPersisted());

        ArgumentCaptor<SysNotificationDispatchLog> logCaptor =
                ArgumentCaptor.forClass(SysNotificationDispatchLog.class);
        verify(dispatchLogMapper).insert(logCaptor.capture());
        SysNotificationDispatchLog dispatchLog = logCaptor.getValue();
        assertEquals(SysNotificationDispatchLog.STATUS_SENT, dispatchLog.getDispatchStatus());
        assertEquals(message.getId(), dispatchLog.getMessageId());
        assertEquals(SysNotificationDispatchLog.CHANNEL_IN_APP, dispatchLog.getChannel());
        assertNotNull(dispatchLog.getSentTime());
    }

    @Test
    @DisplayName("提醒窗口内已通知: 幂等跳过，不重复写站内信")
    void shouldSkipWhenAlreadyNotifiedInWindow() {
        stubOverdue(List.of(overdueTodo("TODO002", "user-approver")));
        stubAlreadyNotifiedCount(1L);

        scheduler.scanOverdueTodos();

        verify(messageMapper, never()).insert(any(SysMessage.class));
        verify(dispatchLogMapper, never()).insert(any(SysNotificationDispatchLog.class));
        verify(pushService, never()).push(any(RealtimeEnvelope.class));
    }

    @Test
    @DisplayName("无办理人: 跳过，不产生 receiver_id 为空的站内信")
    void shouldSkipTodoWithoutAssignee() {
        stubOverdue(List.of(overdueTodo("TODO003", "  ")));

        scheduler.scanOverdueTodos();

        verify(messageMapper, never()).insert(any(SysMessage.class));
        verify(pushService, never()).push(any(RealtimeEnvelope.class));
    }

    @Test
    @DisplayName("推送异常: 站内信仍落库，分发日志记 FAILED + last_error + next_retry_time")
    void shouldRecordFailedDispatchWhenPushThrows() {
        stubOverdue(List.of(overdueTodo("TODO004", "user-approver")));
        stubAlreadyNotifiedCount(0L);
        doThrow(new IllegalStateException("ws session closed")).when(pushService).push(any(RealtimeEnvelope.class));

        scheduler.scanOverdueTodos();

        verify(messageMapper).insert(any(SysMessage.class));
        ArgumentCaptor<SysNotificationDispatchLog> logCaptor =
                ArgumentCaptor.forClass(SysNotificationDispatchLog.class);
        verify(dispatchLogMapper).insert(logCaptor.capture());
        SysNotificationDispatchLog dispatchLog = logCaptor.getValue();
        assertEquals(SysNotificationDispatchLog.STATUS_FAILED, dispatchLog.getDispatchStatus());
        assertEquals("ws session closed", dispatchLog.getLastError());
        assertNotNull(dispatchLog.getNextRetryTime(), "失败分发应预置下次重试时间");
    }

    @Test
    @DisplayName("无超时待办: 不产生任何写入")
    void shouldDoNothingWhenNoOverdueTodo() {
        stubOverdue(List.of());

        scheduler.scanOverdueTodos();

        verify(messageMapper, never()).insert(any(SysMessage.class));
        verify(dispatchLogMapper, never()).insert(any(SysNotificationDispatchLog.class));
        verify(pushService, never()).push(any(RealtimeEnvelope.class));
    }

    @Test
    @DisplayName("单条失败不阻断后续待办处理")
    void shouldContinueAfterSingleFailure() {
        stubOverdue(List.of(overdueTodo("TODO005", "user-a"), overdueTodo("TODO006", "user-b")));
        stubAlreadyNotifiedCount(0L);
        when(messageMapper.insert(any(SysMessage.class)))
                .thenThrow(new IllegalStateException("db down"))
                .thenReturn(1);

        scheduler.scanOverdueTodos();

        verify(messageMapper, org.mockito.Mockito.times(2)).insert(any(SysMessage.class));
        verify(dispatchLogMapper, org.mockito.Mockito.times(1))
                .insert(any(SysNotificationDispatchLog.class));
    }
}
