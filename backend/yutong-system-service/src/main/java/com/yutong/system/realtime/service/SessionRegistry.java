package com.yutong.system.realtime.service;

import com.yutong.system.realtime.dto.RealtimeEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 本地会话注册表。设计来源: 44-实时通信与消息推送设计
 *
 * <p>维护本 JVM 实例上所有已通过 AUTH 的 WebSocket 会话，按 channel（user:{userId} / tenant:{tenantId}）索引。
 * 多实例部署时，每个实例只持有连到自己的 session，跨实例广播由 {@link PushService} 通过 Redis PubSub 完成。
 *
 * <p>线程安全: 使用 ConcurrentHashMap + CopyOnWriteArraySet，单 session 写入由 WebSocket 容器串行化保证。
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    /** channel → sessions。一个用户可能多端登录，故一对多。 */
    private final ConcurrentHashMap<String, Set<WebSocketSession>> channelSessions = new ConcurrentHashMap<>();

    /** sessionId → channel。便于 afterConnectionClosed 反查清理。 */
    private final ConcurrentHashMap<String, String> sessionChannel = new ConcurrentHashMap<>();

    /** 注册会话到指定 channel。 */
    public void register(String channel, WebSocketSession session) {
        channelSessions.computeIfAbsent(channel, k -> ConcurrentHashMap.newKeySet()).add(session);
        sessionChannel.put(session.getId(), channel);
        log.debug("WS session registered id={} channel={} totalInChannel={}",
                session.getId(), channel, channelSessions.get(channel).size());
    }

    /** 注销会话，返回其原 channel（便于清理计数）。 */
    public String unregister(WebSocketSession session) {
        String channel = sessionChannel.remove(session.getId());
        if (channel != null) {
            Set<WebSocketSession> set = channelSessions.get(channel);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    channelSessions.remove(channel);
                }
            }
        }
        return channel;
    }

    /** 推送信封到指定 channel 的所有本机 session。返回成功投递数。 */
    public int sendToChannel(String channel, RealtimeEnvelope envelope, com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        Set<WebSocketSession> set = channelSessions.get(channel);
        if (set == null || set.isEmpty()) {
            return 0;
        }
        int success = 0;
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            log.error("序列化推送信封失败 messageId={} - {}", envelope.getMessageId(), e.getMessage(), e);
            return 0;
        }
        for (WebSocketSession session : set) {
            if (!session.isOpen()) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
                success++;
            } catch (IOException e) {
                log.warn("推送失败 sessionId={} channel={} messageId={} - {}",
                        session.getId(), channel, envelope.getMessageId(), e.getMessage());
            }
        }
        return success;
    }

    /** 当前 channel 的本机会话数。监控/限流用。 */
    public int countByChannel(String channel) {
        Set<WebSocketSession> set = channelSessions.get(channel);
        return set != null ? set.size() : 0;
    }

    /** 本机总会话数。监控用。 */
    public int totalSessions() {
        return sessionChannel.size();
    }
}
