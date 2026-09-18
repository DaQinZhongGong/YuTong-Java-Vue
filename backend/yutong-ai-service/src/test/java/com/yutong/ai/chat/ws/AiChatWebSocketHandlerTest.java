package com.yutong.ai.chat.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.chat.dto.AiChatRequest;
import com.yutong.ai.chat.service.AiChatApplicationService;
import com.yutong.ai.chat.service.ChatStreamListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiChatWebSocketHandlerTest {

    @Test
    @DisplayName("空消息应回推错误且不启动流")
    void emptyContent_shouldSendError() throws Exception {
        AiChatApplicationService chatService = mock(AiChatApplicationService.class);
        AiChatWebSocketHandler handler = new AiChatWebSocketHandler(chatService, new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.handleTextMessage(session, new TextMessage("{\"content\":\"\"}"));

        verify(chatService, never()).streamToListener(any(AiChatRequest.class), any(ChatStreamListener.class));
        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    @DisplayName("合法消息应启动 streamToListener")
    void validContent_shouldStartStream() {
        AiChatApplicationService chatService = mock(AiChatApplicationService.class);
        AiChatWebSocketHandler handler = new AiChatWebSocketHandler(chatService, new ObjectMapper());
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getId()).thenReturn("s1");
        Map<String, Object> attrs = new HashMap<>();
        attrs.put(AiChatHandshakeInterceptor.ATTR_USER_ID, "u1");
        attrs.put(AiChatHandshakeInterceptor.ATTR_TENANT_ID, "default");
        when(session.getAttributes()).thenReturn(attrs);

        handler.handleTextMessage(session, new TextMessage("{\"content\":\"你好\",\"scenario\":\"PLATFORM_QA\"}"));

        verify(chatService).streamToListener(any(AiChatRequest.class), any(ChatStreamListener.class));
    }
}
