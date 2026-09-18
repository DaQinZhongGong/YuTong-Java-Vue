package com.yutong.ai.chat.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.chat.dto.AiChatRequest;
import com.yutong.ai.chat.dto.AiStreamDeltaData;
import com.yutong.ai.chat.dto.AiStreamErrorData;
import com.yutong.ai.chat.dto.AiStreamEvent;
import com.yutong.ai.chat.dto.ChatAttachment;
import com.yutong.ai.chat.service.AiChatApplicationService;
import com.yutong.ai.chat.service.ChatStreamListener;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScopeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 移动端 AI 对话 WebSocket。协议对齐 业界同类实现 mp-chat：
 * 增量 {@code {"content":"..."}}、结束 {@code [DONE]}、错误 {@code {"data":"错误:..."}}。
 */
@Component
public class AiChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AiChatWebSocketHandler.class);

    private final AiChatApplicationService chatService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, Boolean> inflight = new ConcurrentHashMap<>();

    public AiChatWebSocketHandler(AiChatApplicationService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.debug("AI Chat WS 已连接 session={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        JsonNode root;
        try {
            root = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            sendError(session, "错误:消息格式不正确");
            return;
        }
        String content = text(root, "content", "message");
        if (content == null || content.isBlank()) {
            sendError(session, "错误:对话消息不能为空");
            return;
        }
        if (inflight.putIfAbsent(session.getId(), Boolean.TRUE) != null) {
            sendError(session, "错误:上一轮回复尚未结束");
            return;
        }

        restoreUser(session);
        AiChatRequest request = new AiChatRequest();
        request.setMessage(content);
        request.setConversationId(firstNonBlank(text(root, "conversationId"), text(root, "sessionId")));
        request.setScenario(firstNonBlank(text(root, "scenario"), "PLATFORM_QA"));
        request.setKbId(text(root, "kbId", "knowledgeId"));
        request.setProviderCode(text(root, "providerCode"));
        request.setModelCode(firstNonBlank(text(root, "modelCode"), text(root, "model")));
        request.setProviderType(text(root, "providerType"));
        request.setModelType(text(root, "modelType"));
        request.setEndpoint(text(root, "endpoint"));
        request.setAgentId(text(root, "agentId"));
        request.setParentMessageId(text(root, "parentMessageId"));
        request.setSystemPrompt(text(root, "systemPrompt"));
        // 多模态 attachments：优先 attachments 数组（P1-7），回退到单 fileUrl 拼文本
        List<ChatAttachment> attachments = parseAttachments(root.path("attachments"));
        if (attachments.isEmpty()) {
            String fileUrl = text(root, "fileUrl");
            if (fileUrl != null && !fileUrl.isBlank()) {
                request.setMessage(content + "\n\n[附件](" + fileUrl + ")");
            }
        } else {
            request.setAttachments(attachments);
        }

        try {
            chatService.streamToListener(request, new WsChatStreamListener(session));
        } catch (Exception ex) {
            inflight.remove(session.getId());
            log.warn("AI Chat WS 启动流失败: {}", ex.getMessage());
            sendError(session, "错误:" + safeMsg(ex));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        inflight.remove(session.getId());
    }

    private void restoreUser(WebSocketSession session) {
        Map<String, Object> attrs = session.getAttributes();
        CurrentUserContext.set(
                str(attrs.get(AiChatHandshakeInterceptor.ATTR_USER_ID)),
                str(attrs.get(AiChatHandshakeInterceptor.ATTR_TENANT_ID)),
                str(attrs.get(AiChatHandshakeInterceptor.ATTR_USERNAME)),
                str(attrs.get(AiChatHandshakeInterceptor.ATTR_DEPT_ID)),
                str(attrs.get(AiChatHandshakeInterceptor.ATTR_DEPT_PATH)),
                (DataScopeType) attrs.get(AiChatHandshakeInterceptor.ATTR_DATA_SCOPE));
    }

    private void sendError(WebSocketSession session, String msg) {
        sendJson(session, Map.of("data", msg));
    }

    private void sendJson(WebSocketSession session, Object payload) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            }
        } catch (Exception e) {
            log.warn("AI Chat WS 发送失败: {}", e.getMessage());
        }
    }

    private void sendRaw(WebSocketSession session, String raw) {
        if (!session.isOpen()) {
            return;
        }
        try {
            synchronized (session) {
                session.sendMessage(new TextMessage(raw));
            }
        } catch (Exception e) {
            log.warn("AI Chat WS 发送失败: {}", e.getMessage());
        }
    }

    private static String text(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode n = root.get(key);
            if (n != null && !n.isNull() && n.isValueNode()) {
                String v = n.asText();
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }

    /**
     * 解析 WS 消息里的 attachments 数组，转 ChatAttachment。
     * 字段缺失或非数组返回空 list（不抛错，调用方回退到 fileUrl 文本拼接）。
     */
    private static List<ChatAttachment> parseAttachments(JsonNode attachmentsNode) {
        if (attachmentsNode == null || !attachmentsNode.isArray()) {
            return List.of();
        }
        List<ChatAttachment> result = new ArrayList<>();
        Iterator<JsonNode> it = attachmentsNode.elements();
        while (it.hasNext()) {
            JsonNode n = it.next();
            if (n == null || !n.isObject()) continue;
            String url = n.path("url").asText(null);
            if (url == null || url.isBlank()) continue;
            ChatAttachment a = new ChatAttachment();
            a.setType(n.path("type").asText("image"));
            a.setUrl(url);
            a.setName(n.path("name").asText(null));
            a.setFormat(n.path("format").asText(null));
            result.add(a);
        }
        return result;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String safeMsg(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private final class WsChatStreamListener implements ChatStreamListener {
        private final WebSocketSession session;
        private boolean doneSent;

        private WsChatStreamListener(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void onEvent(AiStreamEvent event) {
            if (AiStreamEvent.TYPE_DELTA.equals(event.getEventType()) && event.getData() instanceof AiStreamDeltaData delta) {
                if (delta.getText() != null && !delta.getText().isEmpty()) {
                    sendJson(session, Map.of("content", delta.getText(),
                            "conversationId", event.getConversationId(),
                            "messageId", event.getMessageId()));
                }
                return;
            }
            if (AiStreamEvent.TYPE_ERROR.equals(event.getEventType())) {
                String msg = "错误:流式生成失败";
                if (event.getData() instanceof AiStreamErrorData err && err.getCode() != null) {
                    msg = "错误:" + err.getCode();
                }
                sendError(session, msg);
                return;
            }
            // V052 P2-C: done 帧携带落库 assistant 消息 DB id (移动端反馈/分支挂靠用);
            // 信封 messageId 为单次流临时 id, 不可用于 /messages/{id}/feedback
            if (AiStreamEvent.TYPE_DONE.equals(event.getEventType())
                    && event.getData() instanceof com.yutong.ai.chat.dto.AiStreamDoneData done
                    && done.getMessageId() != null) {
                if (!doneSent) {
                    doneSent = true;
                    sendJson(session, Map.of("done", true,
                            "messageId", done.getMessageId(),
                            "conversationId", event.getConversationId()));
                }
            }
        }

        @Override
        public void onComplete() {
            if (!doneSent) {
                doneSent = true;
                sendRaw(session, "[DONE]");
            }
            inflight.remove(session.getId());
        }

        @Override
        public void onFailure(Throwable error) {
            if (!doneSent) {
                sendError(session, "错误:" + safeMsg(error));
            }
            inflight.remove(session.getId());
        }
    }
}
