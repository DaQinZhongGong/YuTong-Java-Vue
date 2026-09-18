package com.yutong.system.realtime.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;

/**
 * WebSocket 通用工具类。
 * 落点: 业界同类实现 common-websocket + ADR 0005 P3。
 *
 * <p>提供 JSON 序列化发送、安全关闭、会话属性读取等通用能力,
 * 供 AiChatWebSocketHandler / RealtimeWebSocketHandler 共用。
 */
public final class WebSocketUtils {

    private static final Logger log = LoggerFactory.getLogger(WebSocketUtils.class);

    private WebSocketUtils() {}

    /**
     * 发送 JSON 消息。序列化失败或连接断开时仅日志, 不抛异常。
     */
    public static void sendJson(WebSocketSession session, Object payload, ObjectMapper mapper) {
        if (session == null || !session.isOpen()) return;
        try {
            String json = mapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (IOException e) {
            log.warn("[WS] sendJson failed sessionId={}, error={}", session.getId(), e.getMessage());
        } catch (Exception e) {
            log.warn("[WS] sendJson serialize failed: {}", e.getMessage());
        }
    }

    /**
     * 发送原始文本消息。
     */
    public static void sendText(WebSocketSession session, String text) {
        if (session == null || !session.isOpen()) return;
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(text));
            }
        } catch (IOException e) {
            log.warn("[WS] sendText failed sessionId={}, error={}", session.getId(), e.getMessage());
        }
    }

    /**
     * 安静关闭 (忽略异常)。
     */
    public static void closeQuietly(WebSocketSession session, int code, String reason) {
        if (session == null || !session.isOpen()) return;
        try {
            session.close(new org.springframework.web.socket.CloseStatus(code, reason));
        } catch (Exception e) {
            log.debug("[WS] closeQuietly failed: {}", e.getMessage());
        }
    }

    /**
     * 从会话属性获取字符串。
     */
    public static String getSessionAttr(WebSocketSession session, String key) {
        if (session == null) return null;
        Object val = session.getAttributes().get(key);
        return val != null ? String.valueOf(val) : null;
    }

    /**
     * 从 Authorization 头或查询参数提取 Token。
     */
    public static String extractToken(WebSocketSession session) {
        if (session == null) return null;
        // 1. 查询参数 ?token=xxx
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null) {
            for (String pair : query.split("&")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2 && "token".equals(kv[0])) {
                    return kv[1];
                }
            }
        }
        // 2. 会话属性
        return getSessionAttr(session, "token");
    }

    /**
     * 获取客户端 IP (支持代理头)。
     */
    public static String getClientIp(WebSocketSession session) {
        if (session == null) return null;
        String ip = getSessionAttr(session, "X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = getSessionAttr(session, "X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip;
        return session.getRemoteAddress() != null
                ? session.getRemoteAddress().getAddress().getHostAddress()
                : null;
    }
}
