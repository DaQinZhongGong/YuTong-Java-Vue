package com.yutong.ai.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.gateway.service.AiProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智谱 Web Search API 客户端。
 * 联网搜索客户端。
 *
 * <p>API: POST https://open.bigmodel.cn/api/paas/v4/web_search
 * 请求体: {"search_query":"...","search_engine":"search_std","count":10}
 * 响应: {"code":200,"data":{"search_result":[{title,content,link,media,publish_date}]}}
 *
 * <p>安全: 失败关闭 — API Key 未配置/调用失败/非 200 一律抛 SearchException。
 */
@Component
public class ZhipuWebSearchClient implements WebSearchClient {

    private static final Logger log = LoggerFactory.getLogger(ZhipuWebSearchClient.class);

    private static final String DEFAULT_BASE_URL = "https://open.bigmodel.cn/api/paas/v4";
    private static final String SEARCH_PATH = "/web_search";
    private static final int MAX_QUERY_LENGTH = 70;
    private static final int MAX_RESULT_COUNT = 50;

    private final ObjectMapper objectMapper;
    private final AiProviderRegistry providerRegistry;
    private final LlmProviderSelector providerSelector;
    private final HttpClient httpClient;

    @Value("${yutong.ai.web-search.enabled:true}")
    private boolean enabled;

    public ZhipuWebSearchClient(ObjectMapper objectMapper, AiProviderRegistry providerRegistry,
                                 LlmProviderSelector providerSelector) {
        this.objectMapper = objectMapper;
        this.providerRegistry = providerRegistry;
        this.providerSelector = providerSelector;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public SearchResponse search(String query, int resultCount) {
        if (!enabled) {
            throw new SearchException("Web Search 功能未启用");
        }
        if (query == null || query.isBlank()) {
            throw new SearchException("搜索关键词不能为空");
        }
        query = query.trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new SearchException("搜索关键词不能超过 " + MAX_QUERY_LENGTH + " 个字符");
        }
        int count = Math.max(1, Math.min(resultCount, MAX_RESULT_COUNT));

        String apiKey = resolveApiKey();
        String requestId = UUID.randomUUID().toString();

        try {
            Map<String, Object> body = Map.of(
                    "search_query", query,
                    "search_engine", "search_std",
                    "count", count,
                    "request_id", requestId
            );
            String json = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(DEFAULT_BASE_URL + SEARCH_PATH))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new SearchException("智谱 Web Search 调用失败: HTTP " + response.statusCode());
            }

            return parseResponse(query, requestId, response.body());
        } catch (SearchException e) {
            throw e;
        } catch (Exception e) {
            log.warn("[WebSearch] call failed query={}, error={}", query, e.getMessage());
            throw new SearchException("智谱 Web Search 调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析响应 JSON。失败关闭: 解析失败抛 SearchException。
     */
    SearchResponse parseResponse(String query, String requestId, String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);

            // 智谱 API 返回 code=200 表示成功
            int code = root.path("code").asInt(0);
            if (code != 200 && code != 0) {
                String msg = root.path("message").asText(root.path("msg").asText("未知错误"));
                throw new SearchException("智谱 Web Search 返回错误: code=" + code + ", msg=" + msg);
            }

            JsonNode resultsNode = root.path("data").path("search_result");
            if (resultsNode.isMissingNode() || resultsNode.isNull()) {
                // 兼容另一种响应格式
                resultsNode = root.path("search_result");
            }

            List<SearchResult> results = new ArrayList<>();
            if (resultsNode.isArray()) {
                for (JsonNode item : resultsNode) {
                    results.add(new SearchResult(
                            item.path("title").asText(""),
                            item.path("content").asText(""),
                            item.path("link").asText(""),
                            item.path("media").asText(""),
                            item.path("publish_date").asText("")
                    ));
                }
            }

            return new SearchResponse(query, requestId, results.size(), results);
        } catch (SearchException e) {
            throw e;
        } catch (Exception e) {
            throw new SearchException("搜索结果解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 AiProviderRegistry 解析智谱 API Key。
     * 失败关闭: 无可用 Key 时抛 SearchException。
     */
    private String resolveApiKey() {
        try {
            var providers = providerRegistry.listAllEnabled();
            for (var p : providers) {
                if ("zhipu".equalsIgnoreCase(p.getProviderType())
                        || "zhipu".equalsIgnoreCase(p.getProviderCode())) {
                    String key = providerSelector.resolveApiKey(p.getApiKeyRef());
                    if (key != null && !key.isBlank()
                            && !"sk_xx".equalsIgnoreCase(key)
                            && !"your_api_key".equalsIgnoreCase(key)) {
                        return key;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[WebSearch] resolveApiKey from registry failed: {}", e.getMessage());
        }
        throw new SearchException("智谱 API Key 未配置, 请在 AI 供应商管理中添加智谱供应商");
    }
}
