package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.util.List;

/**
 * OpenAI 兼容协议适配器。
 * 支持所有提供 /v1/chat/completions 的供应商：SiliconFlow、OpenRouter、Groq、智谱、DeepSeek、QwenPaw 等。
 * 设计来源: P6-02 免费 LLM 供应商集成
 * <p>
 * 实现采用 Java 原生 {@link HttpClient}，避免 RestClient 默认拦截器在匿名免 Key 场景下携带额外头信息，
 * 确保 Pollinations 等公共端点稳定走匿名路径。
 */
public class OpenAiCompatibleAdapter implements LlmProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAdapter.class);

    /** 协议编码，与 ai_provider.protocol 对齐 */
    public static final String PROTOCOL = "OPENAI_COMPATIBLE";

    /** OpenAI 兼容 chat completions 相对路径；endpoint 应为 OpenAI 兼容基础路径，
     * 如 /v1、/v1beta/openai、/compatible-mode/v1、/compatibility/v1 等 */
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /** Pollinations 匿名模式下用户消息最大长度，超过则截断；保守值以降低触发免费额度限制的概率 */
    private static final int POLLINATIONS_ANONYMOUS_MAX_CONTENT_LENGTH = 200;
    private static final String POLLINATIONS_ANONYMOUS_TRUNCATION_SUFFIX = "\n...[内容已截断，请精简问题]";
    /** Pollinations 匿名免费 tier 仅支持 max_tokens=1，超过会触发 402 */
    private static final int POLLINATIONS_ANONYMOUS_MAX_TOKENS = 1;

    private final AiProvider provider;
    private final String apiKey;
    private final ObjectMapper objectMapper;
    private final HttpExecutor httpExecutor;

    public OpenAiCompatibleAdapter(AiProvider provider, String apiKey, ObjectMapper objectMapper) {
        this(provider, apiKey, objectMapper, buildDefaultHttpExecutor(provider));
    }

    public OpenAiCompatibleAdapter(AiProvider provider, String apiKey,
                                   ObjectMapper objectMapper, HttpExecutor httpExecutor) {
        this.provider = provider;
        this.apiKey = apiKey == null ? "" : apiKey;
        this.objectMapper = objectMapper;
        this.httpExecutor = httpExecutor;
    }

    @Override
    public boolean supports(String protocol) {
        return PROTOCOL.equalsIgnoreCase(protocol);
    }

    @Override
    public LlmResponse chat(LlmRequest request) {
        long startTime = System.currentTimeMillis();
        String endpoint = provider.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "供应商 endpoint 不能为空: " + provider.getProviderCode());
        }
        if (containsPathVariable(endpoint)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "供应商 endpoint 包含未替换的路径变量，请先配置: " + provider.getProviderCode());
        }

        String url = buildUrl(endpoint, CHAT_COMPLETIONS_PATH);
        String body = buildRequestBody(request);

        log.debug("llm request start: provider={} model={} url={} messageCount={}",
                provider.getProviderCode(), request.model(), url, request.messages().size());
        if (apiKey.isBlank()) {
            log.debug("llm request anonymous mode: provider={}", provider.getProviderCode());
        }
        log.debug("llm request body: provider={} body={}", provider.getProviderCode(), body);

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(resolveTimeoutMs()))
                    .header(HttpHeaders.CONTENT_TYPE, "application/json");
            if (!apiKey.isBlank()) {
                reqBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }
            HttpRequest httpRequest = reqBuilder
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            // 调试日志：打印实际发送的请求头，便于排查匿名供应商被误判为认证请求的问题
            httpRequest.headers().map().forEach((name, values) ->
                    log.debug("llm request header: provider={} name={} values={}",
                            provider.getProviderCode(), name, String.join(",", values)));

            HttpResponse<String> resp = httpExecutor.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            log.debug("llm request done: provider={} model={} latencyMs={} status={} bodyLen={}",
                    provider.getProviderCode(), request.model(), latencyMs,
                    resp.statusCode(), resp.body() == null ? 0 : resp.body().length());

            if (resp.statusCode() != 200) {
                return LlmResponse.error(provider.getProviderCode(), request.model(),
                        new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                                "LLM 返回非 200: " + resp.statusCode() + " " + resp.body()));
            }
            return parseResponse(resp.body(), request.model(), latencyMs);
        } catch (Exception e) {
            int latencyMs = (int) (System.currentTimeMillis() - startTime);
            log.warn("llm request failed: provider={} model={} latencyMs={} err={}",
                    provider.getProviderCode(), request.model(), latencyMs, e.getMessage());
            return LlmResponse.error(provider.getProviderCode(), request.model(), e);
        }
    }

    private String buildRequestBody(LlmRequest request) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", request.model());
        List<LlmMessage> effectiveMessages = request.messages();
        boolean anonymousPollinations = apiKey.isBlank() && "pollinations".equals(provider.getProviderCode());
        // Pollinations 匿名免费 tier 仅支持单条 user 消息；携带 system/history 会触发 402 认证计费路径。
        if (anonymousPollinations) {
            effectiveMessages = request.messages().stream()
                    .filter(m -> "user".equals(m.role()))
                    .reduce((first, second) -> second)
                    .map(msg -> List.of(truncateIfNeeded(msg)))
                    .orElse(request.messages());
            log.debug("pollinations anonymous mode: use only last user message, originalCount={} effectiveCount={}",
                    request.messages().size(), effectiveMessages.size());
        }

        ArrayNode messagesNode = body.putArray("messages");
        for (LlmMessage message : effectiveMessages) {
            ObjectNode messageNode = messagesNode.addObject();
            messageNode.put("role", message.role());
            messageNode.put("content", message.content());
        }

        body.put("temperature", request.temperature());
        if (anonymousPollinations) {
            // Pollinations 匿名免费 tier 仅支持 max_tokens=1，超过会触发 402；不设置也会触发 402。
            body.put("max_tokens", POLLINATIONS_ANONYMOUS_MAX_TOKENS);
        } else if (request.maxTokens() != null && request.maxTokens() > 0) {
            body.put("max_tokens", request.maxTokens());
        }
        body.put("stream", false);
        return body.toString();
    }

    private LlmMessage truncateIfNeeded(LlmMessage message) {
        if (message.content().length() <= POLLINATIONS_ANONYMOUS_MAX_CONTENT_LENGTH) {
            return message;
        }
        String truncated = message.content().substring(0, POLLINATIONS_ANONYMOUS_MAX_CONTENT_LENGTH)
                + POLLINATIONS_ANONYMOUS_TRUNCATION_SUFFIX;
        return new LlmMessage(message.role(), truncated);
    }

    private LlmResponse parseResponse(String responseBody, String modelCode, int latencyMs) {
        if (responseBody == null || responseBody.isBlank()) {
            return LlmResponse.error(provider.getProviderCode(), modelCode,
                    new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "LLM 返回为空"));
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return LlmResponse.error(provider.getProviderCode(), modelCode,
                        new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                                "LLM 返回 choices 为空: " + responseBody));
            }
            JsonNode messageNode = choices.get(0).path("message");
            String content = messageNode.path("content").asText("");
            String finishReason = choices.get(0).path("finish_reason").asText();

            JsonNode usage = root.path("usage");
            int tokenInput = usage.path("prompt_tokens").asInt(0);
            int tokenOutput = usage.path("completion_tokens").asInt(0);

            // 兜底：若供应商未返回 usage，按字符数估算
            if (tokenInput == 0 && tokenOutput == 0) {
                tokenInput = estimateTokensFromMessages(List.of());
                tokenOutput = estimateTokens(content);
            }

            return new LlmResponse(
                    provider.getProviderCode(), modelCode, content,
                    tokenInput, tokenOutput, latencyMs, finishReason, null);
        } catch (Exception e) {
            return LlmResponse.error(provider.getProviderCode(), modelCode, e);
        }
    }

    private static HttpExecutor buildDefaultHttpExecutor(AiProvider provider) {
        int timeoutMs = provider.getTimeoutMs() == null ? 60000 : provider.getTimeoutMs();
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        return httpClient::send;
    }

    private long resolveTimeoutMs() {
        return provider.getTimeoutMs() == null ? 60000L : provider.getTimeoutMs().longValue();
    }

    private String buildUrl(String endpoint, String path) {
        if (endpoint.endsWith("/") && path.startsWith("/")) {
            return endpoint + path.substring(1);
        }
        if (!endpoint.endsWith("/") && !path.startsWith("/")) {
            return endpoint + "/" + path;
        }
        return endpoint + path;
    }

    private boolean containsPathVariable(String endpoint) {
        return endpoint != null && endpoint.contains("{") && endpoint.contains("}");
    }

    private int estimateTokensFromMessages(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (LlmMessage message : messages) {
            total += estimateTokens(message.content());
        }
        return total;
    }

    private int estimateTokens(String text) {
        if (text == null) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 2.0);
    }

    /**
     * HTTP 执行抽象，便于单元测试注入 mock，生产环境使用 HttpClient。
     */
    @FunctionalInterface
    interface HttpExecutor {
        HttpResponse<String> send(HttpRequest request, HttpResponse.BodyHandler<String> handler)
                throws Exception;
    }
}
