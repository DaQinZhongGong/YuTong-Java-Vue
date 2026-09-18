package com.yutong.system.realtime.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.WebSocketSession;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * WebSocketUtils 单元测试。
 */
class WebSocketUtilsTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sendJson_validSession_sends() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        WebSocketUtils.sendJson(session, Map.of("type", "ping"), mapper);
        verify(session).sendMessage(any());
    }

    @Test
    void sendJson_closedSession_noSend() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(false);
        WebSocketUtils.sendJson(session, Map.of("type", "ping"), mapper);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendJson_nullSession_noThrow() {
        assertDoesNotThrow(() -> WebSocketUtils.sendJson(null, Map.of(), mapper));
    }

    @Test
    void sendText_validSession_sends() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        WebSocketUtils.sendText(session, "hello");
        verify(session).sendMessage(any());
    }

    @Test
    void closeQuietly_validSession_closes() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        assertDoesNotThrow(() -> WebSocketUtils.closeQuietly(session, 1000, "done"));
    }

    @Test
    void closeQuietly_nullSession_noThrow() {
        assertDoesNotThrow(() -> WebSocketUtils.closeQuietly(null, 1000, "done"));
    }

    @Test
    void getSessionAttr_returnsValue() {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("userId", "user-1");
        when(session.getAttributes()).thenReturn(attrs);
        assertEquals("user-1", WebSocketUtils.getSessionAttr(session, "userId"));
        assertNull(WebSocketUtils.getSessionAttr(session, "nonexistent"));
    }

    @Test
    void extractToken_fromQueryParam() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(URI.create("ws://localhost/ws?token=abc123"));
        when(session.getAttributes()).thenReturn(new HashMap<>());
        assertEquals("abc123", WebSocketUtils.extractToken(session));
    }

    @Test
    void extractToken_fromSessionAttr() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getUri()).thenReturn(null);
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("token", "attr-token");
        when(session.getAttributes()).thenReturn(attrs);
        assertEquals("attr-token", WebSocketUtils.extractToken(session));
    }

    @Test
    void getClientIp_fromRemoteAddress() {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.getRemoteAddress()).thenReturn(new InetSocketAddress("192.168.1.1", 8080));
        assertEquals("192.168.1.1", WebSocketUtils.getClientIp(session));
    }
}
