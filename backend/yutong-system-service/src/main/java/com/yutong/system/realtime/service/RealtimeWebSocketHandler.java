package com.yutong.system.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.realtime.config.RealtimeProperties;
import com.yutong.system.realtime.dto.ClientCommand;
import com.yutong.system.realtime.dto.RealtimeEnvelope;
import com.yutong.system.realtime.dto.ServerAck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 主处理器。设计来源: 44-实时通信与消息推送设计 line 71-127
 *
 * <p>连接生命周期:
 * <ol>
 *   <li>afterConnectionEstablished: 不立即订阅，等首帧 AUTH</li>
 *   <li>handleTextMessage: 按 action 分发
 *     <ul>
 *       <li>AUTH: 首帧鉴权（X-Mock-User 由 handshake interceptor 已写入 CurrentUserContext，
 *           真实模式用 Sa-Token StpUtil.login(token) 校验）。鉴权成功后自动订阅 user:{userId}，
 *           可选 tenant:{tenantId}</li>
 *       <li>PING: 回 PONG（type=PING 信封）</li>
 *       <li>SUBSCRIBE: 校验 channel 归属（禁止跨租户）</li>
 *       <li>ACK_DELIVERED/ACK_READ/ACK_HANDLED/ACK_FAILED: 同步回执，业务幂等去重</li>
 *     </ul>
 *   </li>
 *   <li>afterConnectionClosed: 从 SessionRegistry 注销</li>
 * </ol>
 *
 * <p>安全约束（44 号文档 line 152-159）:
 * <ul>
 *   <li>未 AUTH 前不接受 SUBSCRIBE/ACK/业务消息</li>
 *   <li>订阅频道必须由服务端从登录上下文生成，忽略客户端传入的 userId/tenantId</li>
 *   <li>频繁重连、ACK 异常需写安全审计（第一版仅日志，留 v1.1+ 接入 operation_log）</li>
 * </ul>
 */
public class RealtimeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(RealtimeWebSocketHandler.class);

    /** session attributes key: 已 AUTH 标记 */
    public static final String ATTR_AUTHED = "yutong.authed";
    /** session attributes key: 当前用户 ID */
    public static final String ATTR_USER_ID = "yutong.userId";
    /** session attributes key: 当前租户 ID */
    public static final String ATTR_TENANT_ID = "yutong.tenantId";
    /** session attributes key: 已订阅频道列表 */
    public static final String ATTR_SUBSCRIBED_CHANNELS = "yutong.channels";

    private final RealtimeProperties properties;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    public RealtimeWebSocketHandler(RealtimeProperties properties,
                                    SessionRegistry sessionRegistry,
                                    ObjectMapper objectMapper) {
        this.properties = properties;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("WebSocket 连接建立 sessionId={} remote={}", session.getId(), session.getRemoteAddress());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        ClientCommand cmd;
        try {
            cmd = objectMapper.readValue(payload, ClientCommand.class);
        } catch (Exception e) {
            sendAck(session, ServerAck.error("PARSE", "SYS-400001", "指令解析失败: " + e.getMessage()));
            return;
        }
        if (cmd.getAction() == null) {
            sendAck(session, ServerAck.error("MISSING_ACTION", "SYS-400001", "action 字段必填"));
            return;
        }
        switch (cmd.getAction()) {
            case ClientCommand.ACTION_AUTH -> handleAuth(session, cmd);
            case ClientCommand.ACTION_PING -> handlePing(session);
            case ClientCommand.ACTION_SUBSCRIBE -> handleSubscribe(session, cmd);
            case ClientCommand.ACTION_ACK_DELIVERED -> handleAck(session, cmd, "DELIVERED");
            case ClientCommand.ACTION_ACK_READ -> handleAck(session, cmd, "READ");
            case ClientCommand.ACTION_ACK_HANDLED -> handleAck(session, cmd, "HANDLED");
            case ClientCommand.ACTION_ACK_FAILED -> handleAck(session, cmd, "FAILED");
            default -> sendAck(session, ServerAck.error(cmd.getAction(), "SYS-400001", "未知 action: " + cmd.getAction()));
        }
    }

    /** AUTH: 从 handshake interceptor 写入的 CurrentUserContext 取上下文（Mock 或 Sa-Token 已校验）。 */
    private void handleAuth(WebSocketSession session, ClientCommand cmd) {
        // handshake interceptor 已校验 token / X-Mock-User 并写入 attributes
        Map<String, Object> attrs = session.getAttributes();
        String userId = (String) attrs.get(ATTR_USER_ID);
        String tenantId = (String) attrs.get(ATTR_TENANT_ID);
        if (userId == null || userId.isBlank()) {
            sendAck(session, ServerAck.error(ClientCommand.ACTION_AUTH, "SYS-401001", "未登录或 token 失效"));
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        attrs.put(ATTR_AUTHED, Boolean.TRUE);
        // 使用 ConcurrentHashMap.newKeySet() 而非 keySet()，前者返回可变的 Set，后者不可变
        attrs.computeIfAbsent(ATTR_SUBSCRIBED_CHANNELS, k -> ConcurrentHashMap.newKeySet());

        // AUTH 成功后自动订阅 user:{userId}
        String userChannel = "user:" + userId;
        sessionRegistry.register(userChannel, session);
        @SuppressWarnings("unchecked")
        java.util.Set<String> channels = (java.util.Set<String>) attrs.get(ATTR_SUBSCRIBED_CHANNELS);
        channels.add(userChannel);

        // 可选自动订阅 tenant:{tenantId}
        if (properties.isAllowTenantSubscribe() && tenantId != null && !tenantId.isBlank()) {
            String tenantChannel = "tenant:" + tenantId;
            sessionRegistry.register(tenantChannel, session);
            channels.add(tenantChannel);
        }

        log.info("WebSocket AUTH 成功 sessionId={} userId={} tenantId={} channels={}",
                session.getId(), userId, tenantId, channels);
        sendAck(session, ServerAck.ok(ClientCommand.ACTION_AUTH,
                "AUTH 成功，已订阅 " + String.join(",", channels)));
    }

    private void handlePing(WebSocketSession session) {
        if (!isAuthed(session)) {
            sendAck(session, ServerAck.error(ClientCommand.ACTION_PING, "SYS-401001", "未 AUTH"));
            return;
        }
        // 回 PONG（type=PING 信封，客户端按 type 识别）
        Map<String, Object> attrs = session.getAttributes();
        RealtimeEnvelope pong = new RealtimeEnvelope(
                (String) attrs.get(ATTR_USER_ID),
                (String) attrs.get(ATTR_TENANT_ID),
                RealtimeEnvelope.TYPE_PING, "PING", "PONG", "PONG",
                null, null, TraceContext.getTraceId());
        sendEnvelope(session, pong);
    }

    private void handleSubscribe(WebSocketSession session, ClientCommand cmd) {
        if (!isAuthed(session)) {
            sendAck(session, ServerAck.error(ClientCommand.ACTION_SUBSCRIBE, "SYS-401001", "未 AUTH"));
            return;
        }
        Map<String, Object> attrs = session.getAttributes();
        String userId = (String) attrs.get(ATTR_USER_ID);
        String tenantId = (String) attrs.get(ATTR_TENANT_ID);
        String channel = cmd.getChannel();
        if (channel == null || channel.isBlank()) {
            sendAck(session, ServerAck.error(ClientCommand.ACTION_SUBSCRIBE, "SYS-400001", "channel 必填"));
            return;
        }
        // 严格校验 channel 归属，禁止跨租户/跨用户订阅
        if (!channel.equals("user:" + userId)
                && !(properties.isAllowTenantSubscribe() && channel.equals("tenant:" + tenantId))) {
            log.warn("WebSocket 越权订阅拦截 sessionId={} userId={} attemptedChannel={}",
                    session.getId(), userId, channel);
            sendAck(session, ServerAck.error(ClientCommand.ACTION_SUBSCRIBE, "AUTH-403001",
                    "禁止订阅非本人/非本租户频道"));
            return;
        }
        sessionRegistry.register(channel, session);
        @SuppressWarnings("unchecked")
        java.util.Set<String> channels = (java.util.Set<String>) attrs.get(ATTR_SUBSCRIBED_CHANNELS);
        channels.add(channel);
        sendAck(session, ServerAck.ok(ClientCommand.ACTION_SUBSCRIBE, "已订阅 " + channel));
    }

    /** ACK 处理: 第一版仅记录日志，业务幂等去重由消费方实现（44 号文档 line 96/124）。 */
    private void handleAck(WebSocketSession session, ClientCommand cmd, String ackType) {
        if (!isAuthed(session)) {
            sendAck(session, ServerAck.error(cmd.getAction(), "SYS-401001", "未 AUTH"));
            return;
        }
        Map<String, Object> attrs = session.getAttributes();
        log.debug("WebSocket ACK sessionId={} userId={} type={} messageId={} errorCode={}",
                session.getId(), attrs.get(ATTR_USER_ID), ackType, cmd.getMessageId(), cmd.getErrorCode());
        sendAck(session, ServerAck.ok(cmd.getAction(), "ACK " + ackType + " 已记录"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String channel = sessionRegistry.unregister(session);
        log.info("WebSocket 连接关闭 sessionId={} channel={} status={} totalSessions={}",
                session.getId(), channel, status, sessionRegistry.totalSessions());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("WebSocket 传输错误 sessionId={} - {}", session.getId(), exception.getMessage());
        sessionRegistry.unregister(session);
    }

    private boolean isAuthed(WebSocketSession session) {
        Object authed = session.getAttributes().get(ATTR_AUTHED);
        return Boolean.TRUE.equals(authed);
    }

    private void sendAck(WebSocketSession session, ServerAck ack) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ack)));
        } catch (IOException e) {
            log.warn("发送 ACK 失败 sessionId={} - {}", session.getId(), e.getMessage());
        }
    }

    private void sendEnvelope(WebSocketSession session, RealtimeEnvelope envelope) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
        } catch (IOException e) {
            log.warn("发送信封失败 sessionId={} - {}", session.getId(), e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (IOException e) {
            log.warn("关闭 session 失败 - {}", e.getMessage());
        }
    }
}
