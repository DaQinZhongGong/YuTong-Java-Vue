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
 * Coze Chat API 适配器。POST {endpoint}/v3/chat，失败返回 error，禁止 mock 假回复。
 */
public class CozeAdapter implements LlmProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(CozeAdapter.class);

    public static final String PROTOCOL = "COZE";
    public static final String PROVIDER_CODE = "coze";

    private final AiProvider provider;
    private final String apiKey;
    private final ObjectMapper objectMapper;

    public CozeAdapter(AiProvider provider, String apiKey, ObjectMapper objectMapper) {
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
                || "coze".equals(p);
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        long start = System.currentTimeMillis();
        String endpoint = provider.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Coze endpoint 不能为空: " + provider.getProviderCode());
        }
        try {
            String url = buildUrl(endpoint, "/v3/chat");
            String body = buildCozeBody(request);
            log.debug("coze chat start: provider={} model={} url={}", provider.getProviderCode(), request.model(), url);
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
                String content = parseCozeResponse(resp.body());
                if (content != null) {
                    return new LlmResponse(provider.getProviderCode(), request.model(), content,
                            estimateTokens(request.messages().toString()), estimateTokens(content), latencyMs, "stop", null);
                }
            }
            String snippet = resp.body() == null ? "" : resp.body().substring(0, Math.min(240, resp.body().length()));
            return LlmResponse.error(provider.getProviderCode(), request.model(),
                    new IllegalStateException("Coze HTTP " + resp.statusCode() + " " + snippet));
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
            onError.accept(new BusinessException(ErrorCode.SYS_PARAM_INVALID, "Coze endpoint 不能为空"));
            return;
        }
        try {
            String lastUser = lastUserContent(request);
            String url = buildUrl(endpoint, "/v3/chat");
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
                    builder.POST(HttpRequest.BodyPublishers.ofString(buildCozeBody(request, true))).build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() != 200) {
                String errBody;
                try (java.io.InputStream is = resp.body()) {
                    errBody = is == null ? "" : new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
                onError.accept(new IllegalStateException("Coze stream HTTP " + resp.statusCode() + " " + errBody));
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
                    String event = root.path("event").asText(root.path("type").asText(""));
                    if ("error".equals(event) || root.has("error")) {
                        onError.accept(new IllegalStateException(root.path("msg").asText(root.path("message").asText("coze stream error"))));
                        return;
                    }
                    String delta = extractStreamDelta(root);
                    if (delta != null && !delta.isBlank()) {
                        outTokens += delta.length();
                        onDelta.accept(delta);
                    }
                    if ("done".equals(event) || "completed".equals(event) || "conversation.chat.completed".equals(event)) {
                        break;
                    }
                }
            }
            onFinish.accept(new StreamFinish("stop", estimateTokens(lastUser), outTokens));
        } catch (Exception e) {
            onError.accept(e);
        }
    }

    private String buildCozeBody(LlmRequest request) {
        return buildCozeBody(request, false);
    }

    private String buildCozeBody(LlmRequest request, boolean stream) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("user_id", "yutong-user");
        body.put("query", lastUserContent(request));
        body.put("stream", stream);
        body.put("bot_id", resolveBotId(request.model()));
        return body.toString();
    }

    private String lastUserContent(LlmRequest request) {
        // Coze /v3/chat 仅接受 query 字符串；多模态折叠为文本部分（图片走单独 file 上传链路）
        return request.messages().stream()
                .filter(m -> "user".equals(m.role()))
                .reduce((a, b) -> b)
                .map(m -> m.textContent())
                .orElse("");
    }

    private String resolveBotId(String fallback) {
        try {
            if (provider.getConfigJson() != null && !provider.getConfigJson().isBlank()) {
                JsonNode cfg = objectMapper.readTree(provider.getConfigJson());
                if (cfg.has("bot_id")) {
                    return cfg.path("bot_id").asText(fallback);
                }
                if (cfg.has("app_id")) {
                    return cfg.path("app_id").asText(fallback);
                }
            }
        } catch (Exception ignored) {
            // keep fallback
        }
        return fallback;
    }

    private String extractStreamDelta(JsonNode root) {
        if (root.has("content") && root.path("content").isValueNode()) {
            return root.path("content").asText("");
        }
        JsonNode delta = root.path("delta");
        if (delta.has("content")) {
            return delta.path("content").asText("");
        }
        JsonNode data = root.path("data");
        if (data.has("content")) {
            return data.path("content").asText("");
        }
        if ("answer".equals(data.path("type").asText()) && data.has("content")) {
            return data.path("content").asText("");
        }
        return null;
    }

    private String parseCozeResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            // Coze 结构多样，尝试多路径
            if (root.has("data") && root.path("data").has("messages")) {
                JsonNode msgs = root.path("data").path("messages");
                if (msgs.isArray() && !msgs.isEmpty()) {
                    for (JsonNode m : msgs) {
                        if ("answer".equals(m.path("type").asText()) || "assistant".equals(m.path("role").asText())) {
                            if (m.has("content")) return m.path("content").asText(null);
                        }
                    }
                    return msgs.get(0).path("content").asText(null);
                }
            }
            if (root.has("messages") && root.path("messages").isArray()) {
                JsonNode msgs = root.path("messages");
                for (JsonNode m : msgs) {
                    if (m.has("content")) return m.path("content").asText(null);
                }
            }
            if (root.has("answer")) return root.path("answer").asText(null);
            if (root.has("content")) return root.path("content").asText(null);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return choices.get(0).path("message").path("content").asText(null);
            }
            return null;
        } catch (Exception e) {
            log.debug("coze parse failed: {}", e.getMessage());
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
