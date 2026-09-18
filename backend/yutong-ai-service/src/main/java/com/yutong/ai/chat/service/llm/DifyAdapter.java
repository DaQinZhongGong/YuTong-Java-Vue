package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Dify Chat API 适配器。POST {endpoint}/chat-messages，失败返回 error，禁止 mock 假回复。
 */
public class DifyAdapter implements LlmProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(DifyAdapter.class);

    public static final String PROTOCOL = "DIFY";
    public static final String PROVIDER_CODE = "dify";

    private final AiProvider provider;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public DifyAdapter(AiProvider provider, String apiKey, ObjectMapper objectMapper) {
        this.provider = provider;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String protocol) {
        if (protocol == null || protocol.isBlank()) return false;
        String p = protocol.trim().toLowerCase();
        return PROTOCOL.toLowerCase().equals(p)
                || PROVIDER_CODE.equals(p)
                || "dify".equals(p);
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        long start = System.currentTimeMillis();
        String endpoint = provider.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Dify endpoint 不能为空: " + provider.getProviderCode());
        }
        try {
            String url = buildUrl(endpoint, "/chat-messages");
            String body = buildDifyBody(request);
            log.debug("dify chat start: provider={} model={} url={}", provider.getProviderCode(), request.model(), url);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(resolveTimeoutMs()))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(resolveTimeoutMs()))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json");
            if (!apiKey.isBlank()) {
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            HttpRequest httpRequest = builder.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int latencyMs = (int) (System.currentTimeMillis() - start);
            if (resp.statusCode() == 200 && resp.body() != null) {
                String content = parseDifyResponse(resp.body());
                if (content != null) {
                    return new LlmResponse(provider.getProviderCode(), request.model(), content,
                            estimateTokens(request.messages().toString()), estimateTokens(content), latencyMs, "stop", null);
                }
            }
            String snippet = resp.body() == null ? "" : resp.body().substring(0, Math.min(240, resp.body().length()));
            return LlmResponse.error(provider.getProviderCode(), request.model(),
                    new IllegalStateException("Dify HTTP " + resp.statusCode() + " " + snippet));
        } catch (Exception e) {
            return LlmResponse.error(provider.getProviderCode(), request.model(), e);
        }
    }

    @Override
    public void stream(LlmRequest request,
                       java.util.function.Consumer<String> onDelta,
                       java.util.function.Consumer<StreamFinish> onFinish,
                       java.util.function.Consumer<Throwable> onError) {
        String endpoint = provider.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            onError.accept(new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Dify endpoint 不能为空"));
            return;
        }
        try {
            String url = buildUrl(endpoint, "/chat-messages");
            ObjectNode body = objectMapper.createObjectNode();
            String lastUser = request.messages().stream()
                    .filter(m -> "user".equals(m.role()))
                    .reduce((a, b) -> b)
                    .map(m -> m.textContent())  // Dify Chat App 私有协议只接受字符串 query；多模态折叠为文本部分
                    .orElse("");
            body.put("query", lastUser);
            body.put("response_mode", "streaming");
            body.put("user", "yutong-user");
            ObjectNode inputs = body.putObject("inputs");
            inputs.put("model", request.model());
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(resolveTimeoutMs()))
                    .build();
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(Math.max(resolveTimeoutMs(), 120_000)))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .header(HttpHeaders.ACCEPT, "text/event-stream");
            if (!apiKey.isBlank()) {
                builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            HttpResponse<java.io.InputStream> resp = client.send(
                    builder.POST(HttpRequest.BodyPublishers.ofString(body.toString())).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                String errBody;
                try (java.io.InputStream is = resp.body()) {
                    errBody = is == null ? "" : new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                onError.accept(new IllegalStateException("Dify stream HTTP " + resp.statusCode() + " " + errBody));
                return;
            }
            int outTokens = 0;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(resp.body(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }
                    JsonNode root = objectMapper.readTree(data);
                    String event = root.path("event").asText("");
                    if ("message".equals(event) || "agent_message".equals(event)) {
                        String answer = root.path("answer").asText("");
                        if (!answer.isBlank()) {
                            outTokens += answer.length();
                            onDelta.accept(answer);
                        }
                    }
                    if ("message_end".equals(event) || "error".equals(event)) {
                        if ("error".equals(event)) {
                            onError.accept(new IllegalStateException(root.path("message").asText("dify stream error")));
                            return;
                        }
                        break;
                    }
                }
            }
            onFinish.accept(new StreamFinish("stop", estimateTokens(lastUser), outTokens));
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private String buildDifyBody(LlmRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        // Dify Chat App 需要 query + inputs + response_mode + user + conversation_id
        String lastUser = request.messages().stream()
                .filter(m -> "user".equals(m.role()))
                .reduce((a, b) -> b)
                .map(m -> m.textContent())  // 阻塞模式同样只接受字符串 query
                .orElse("");
        body.put("query", lastUser);
        body.put("response_mode", "blocking");
        body.put("user", "yutong-user");
        ObjectNode inputs = body.putObject("inputs");
        inputs.put("model", request.model());
        if (request.temperature() != null) inputs.put("temperature", request.temperature());
        return body.toString();
    }

    private String parseDifyResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            // Dify blocking 返回 {"answer": "...", "created_at":..., "message_id":...}
            if (root.has("answer")) return root.path("answer").asText(null);
            if (root.has("data") && root.path("data").has("answer")) return root.path("data").path("answer").asText(null);
            // 兼容 OpenAI 透传
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText(null);
            }
            return null;
        } catch (Exception e) {
            log.debug("dify parse failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildUrl(String endpoint, String path) {
        if (endpoint.endsWith("/") && path.startsWith("/")) return endpoint + path.substring(1);
        if (!endpoint.endsWith("/") && !path.startsWith("/")) return endpoint + "/" + path;
        return endpoint + path;
    }

    private long resolveTimeoutMs() {
        return provider.getTimeoutMs() == null ? 60000L : provider.getTimeoutMs().longValue();
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return (int) Math.ceil(text.length() / 2.0);
    }
}
