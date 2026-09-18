package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * OpenAI 兼容适配器在多模态 content 下的请求体序列化单测。
 * 设计来源: P1-7 多模态视觉 — 验证 messages[].content 正确序列化为 OpenAI 多模态协议。
 */
class OpenAiCompatibleAdapterMultimodalTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiProvider provider;
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> mockResponse = (HttpResponse<String>) mock(HttpResponse.class);
    private OpenAiCompatibleAdapter.HttpExecutor httpExecutor;

    @BeforeEach
    void setUp() {
        provider = new AiProvider();
        provider.setProviderCode("openai");
        provider.setEndpoint("https://api.openai.com/v1");
        provider.setTimeoutMs(30000);

        httpExecutor = mock(OpenAiCompatibleAdapter.HttpExecutor.class);
    }

    @Test
    @DisplayName("文本消息：chat 走通且不报错")
    void textMessageChatOk() throws Exception {
        when(httpExecutor.send(any(HttpRequest.class), any())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("""
                {"choices":[{"message":{"role":"assistant","content":"ok"}}]}
                """);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-test", objectMapper, httpExecutor);
        LlmResponse response = adapter.chat(new LlmRequest("gpt-4o",
                List.of(LlmMessage.user("hi")), 0.7, null, false, "chat"));
        assertFalse(response.isError());
        assertEquals("ok", response.content());
    }

    @Test
    @DisplayName("多模态消息：构造时不报错，chat 路径走通")
    void multimodalMessageChatOk() throws Exception {
        when(httpExecutor.send(any(HttpRequest.class), any())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("""
                {"choices":[{"message":{"role":"assistant","content":"看到了，是一只猫"}}]}
                """);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-test", objectMapper, httpExecutor);
        LlmMessage msg = LlmMessage.userMultimodal("看图",
                List.of("https://example.com/a.png", "data:image/png;base64,xxxx"));
        LlmResponse response = adapter.chat(new LlmRequest("gpt-4o-vision",
                List.of(msg), 0.7, null, false, "chat"));
        assertFalse(response.isError());
        assertEquals("看到了，是一只猫", response.content());
    }

    @Test
    @DisplayName("多模态消息 + 仅图片无文本：chat 路径走通")
    void multimodalImagesOnlyChatOk() throws Exception {
        when(httpExecutor.send(any(HttpRequest.class), any())).thenReturn(mockResponse);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("""
                {"choices":[{"message":{"role":"assistant","content":"reply"}}]}
                """);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-test", objectMapper, httpExecutor);
        LlmMessage msg = LlmMessage.userMultimodal(null, List.of("https://example.com/a.png"));
        LlmResponse response = adapter.chat(new LlmRequest("gpt-4o-vision",
                List.of(msg), 0.7, null, false, "chat"));
        assertFalse(response.isError());
    }

    @Test
    @DisplayName("多模态构造 LlmMessage parts：JSON 序列化对齐 OpenAI 协议")
    void multimodalPartsJsonShape() throws Exception {
        LlmMessage msg = LlmMessage.userMultimodal("看图",
                List.of("https://example.com/a.png"));
        JsonNode parts = objectMapper.valueToTree(msg.content());
        assertTrue(parts.isArray());
        assertEquals(2, parts.size());
        assertEquals("text", parts.path(0).path("type").asText());
        assertEquals("看图", parts.path(0).path("text").asText());
        assertEquals("image_url", parts.path(1).path("type").asText());
        assertEquals("https://example.com/a.png", parts.path(1).path("image_url").path("url").asText());
    }
}
