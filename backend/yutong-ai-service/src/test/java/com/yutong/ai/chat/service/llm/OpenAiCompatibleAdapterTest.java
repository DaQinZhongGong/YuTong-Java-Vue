package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * OpenAI 兼容适配器单元测试。
 * 设计来源: P6-02 免费 LLM 供应商集成
 */
class OpenAiCompatibleAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AiProvider provider;
    private OpenAiCompatibleAdapter.HttpExecutor httpExecutor;
    @SuppressWarnings("unchecked")
    private final HttpResponse<String> mockResponse = mock(HttpResponse.class);

    @BeforeEach
    void setUp() {
        provider = new AiProvider();
        provider.setProviderCode("siliconflow");
        provider.setEndpoint("https://api.siliconflow.cn/v1");
        provider.setTimeoutMs(30000);

        httpExecutor = mock(OpenAiCompatibleAdapter.HttpExecutor.class);
    }

    @Test
    @DisplayName("正常调用返回解析 content / token / model")
    void chatReturnsParsedResponse() throws Exception {
        String responseJson = """
                {
                  "id": "chatcmpl-test",
                  "object": "chat.completion",
                  "model": "deepseek-ai/DeepSeek-R1-0528-Qwen3-8B",
                  "choices": [
                    {
                      "index": 0,
                      "message": { "role": "assistant", "content": "你好，这是测试回复。" },
                      "finish_reason": "stop"
                    }
                  ],
                  "usage": { "prompt_tokens": 12, "completion_tokens": 8, "total_tokens": 20 }
                }
                """;
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseJson);
        when(httpExecutor.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-test", objectMapper, httpExecutor);
        LlmRequest request = new LlmRequest(
                "deepseek-ai/DeepSeek-R1-0528-Qwen3-8B",
                List.of(LlmMessage.system("你是助手"), LlmMessage.user("你好")));

        LlmResponse response = adapter.chat(request);

        assertFalse(response.isError());
        assertEquals("siliconflow", response.providerCode());
        assertEquals("deepseek-ai/DeepSeek-R1-0528-Qwen3-8B", response.modelCode());
        assertEquals("你好，这是测试回复。", response.content());
        assertEquals(12, response.tokenInput());
        assertEquals(8, response.tokenOutput());
        assertEquals("stop", response.finishReason());
        assertTrue(response.latencyMs() >= 0);

        verify(httpExecutor, times(1)).send(argThat(req -> {
            if (!req.uri().equals(URI.create("https://api.siliconflow.cn/v1/chat/completions"))) {
                return false;
            }
            if (!"Bearer sk-test".equals(req.headers().firstValue(HttpHeaders.AUTHORIZATION).orElse(null))) {
                return false;
            }
            return req.method().equals("POST");
        }), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("空 API Key 时不发送 Authorization")
    void chatWithoutApiKeyOmitsAuthHeader() throws Exception {
        String responseJson = """
                {
                  "choices": [
                    { "message": { "role": "assistant", "content": "OK" }, "finish_reason": "stop" }
                  ],
                  "usage": { "prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2 }
                }
                """;
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseJson);
        when(httpExecutor.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "", objectMapper, httpExecutor);
        LlmRequest request = new LlmRequest("model", List.of(LlmMessage.user("hi")));

        LlmResponse response = adapter.chat(request);

        assertFalse(response.isError());
        assertEquals("OK", response.content());

        verify(httpExecutor, times(1)).send(argThat(req ->
                req.headers().firstValue(HttpHeaders.AUTHORIZATION).isEmpty()),
                any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("HTTP 错误返回 error 响应")
    void chatHttpErrorReturnsErrorResponse() throws Exception {
        when(mockResponse.statusCode()).thenReturn(500);
        when(mockResponse.body()).thenReturn("server error");
        when(httpExecutor.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-test", objectMapper, httpExecutor);
        LlmRequest request = new LlmRequest("model", List.of(LlmMessage.user("hi")));

        LlmResponse response = adapter.chat(request);

        assertTrue(response.isError());
        assertNotNull(response.error());
    }

    @Test
    @DisplayName("endpoint 缺少 /v1 时正确拼接路径")
    void chatBuildsUrlWithoutTrailingV1() throws Exception {
        provider.setEndpoint("https://api.example.com");
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn("""
                {"choices":[{"message":{"content":"hi"},"finish_reason":"stop"}],"usage":{"prompt_tokens":1,"completion_tokens":1}}
                """);
        when(httpExecutor.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(mockResponse);

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "sk-test", objectMapper, httpExecutor);
        LlmRequest request = new LlmRequest("model", List.of(LlmMessage.user("hi")));

        LlmResponse response = adapter.chat(request);

        assertFalse(response.isError());
        assertEquals("hi", response.content());

        verify(httpExecutor, times(1)).send(argThat(req ->
                        req.uri().equals(URI.create("https://api.example.com/chat/completions"))),
                any(HttpResponse.BodyHandler.class));
    }

    @Test
    @DisplayName("pollinations 匿名模式下长消息被截断且携带 max_tokens=1")
    void pollinationsAnonymousLongMessageTruncated() throws Exception {
        provider.setProviderCode("pollinations");
        provider.setEndpoint("https://text.pollinations.ai/openai");

        String responseJson = """
                {
                  "choices": [
                    { "message": { "role": "assistant", "content": "OK" }, "finish_reason": "stop" }
                  ],
                  "usage": { "prompt_tokens": 1, "completion_tokens": 1 }
                }
                """;
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(responseJson);

        AtomicReference<String> bodyRef = new AtomicReference<>();
        doAnswer(invocation -> {
            HttpRequest req = invocation.getArgument(0);
            bodyRef.set(collectRequestBody(req));
            return mockResponse;
        }).when(httpExecutor).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        OpenAiCompatibleAdapter adapter = new OpenAiCompatibleAdapter(
                provider, "", objectMapper, httpExecutor);
        String longContent = "A".repeat(400);
        LlmRequest request = new LlmRequest("openai",
                List.of(LlmMessage.system("你是助手"), LlmMessage.user(longContent)));

        LlmResponse response = adapter.chat(request);

        assertFalse(response.isError());
        assertEquals("OK", response.content());

        JsonNode body = objectMapper.readTree(bodyRef.get());
        JsonNode messages = body.path("messages");
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).path("role").asText());
        String content = messages.get(0).path("content").asText();
        assertEquals(200 + "\n...[内容已截断，请精简问题]".length(), content.length());
        assertTrue(content.startsWith("A".repeat(200)));
        assertTrue(content.endsWith("\n...[内容已截断，请精简问题]"));
        // Pollinations 匿名免费 tier 仅支持 max_tokens=1
        assertEquals(1, body.path("max_tokens").asInt());
    }

    private String collectRequestBody(HttpRequest request) {
        if (request.bodyPublisher().isEmpty()) {
            return "";
        }
        Flow.Publisher<ByteBuffer> publisher = request.bodyPublisher().get();
        java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
        publisher.subscribe(new Flow.Subscriber<>() {
            private final java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            private Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer buffer) {
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                try {
                    baos.write(bytes);
                } catch (java.io.IOException e) {
                    future.completeExceptionally(e);
                }
            }

            @Override
            public void onError(Throwable t) {
                future.completeExceptionally(t);
            }

            @Override
            public void onComplete() {
                future.complete(baos.toString(StandardCharsets.UTF_8));
            }
        });
        return future.join();
    }
}
