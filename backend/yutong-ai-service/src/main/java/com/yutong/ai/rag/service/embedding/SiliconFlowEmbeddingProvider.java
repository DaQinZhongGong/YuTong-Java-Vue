package com.yutong.ai.rag.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yutong.ai.gateway.domain.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * SiliconFlow Embedding 供应商 — OpenAI 兼容 /v1/embeddings。
 * endpoint 形如 https://api.siliconflow.cn/v1  则请求 /v1/embeddings。
 */
@Service
public class SiliconFlowEmbeddingProvider implements RemoteEmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(SiliconFlowEmbeddingProvider.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public SiliconFlowEmbeddingProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(TIMEOUT)))
                .build();
    }

    @Override
    public String getProviderType() {
        return "siliconflow";
    }

    @Override
    public boolean supports(AiProvider provider) {
        if (provider == null) return false;
        String pt = provider.getProviderType();
        if ("siliconflow".equalsIgnoreCase(pt)) return true;
        String ep = provider.getEndpoint() != null ? provider.getEndpoint().toLowerCase() : "";
        if (ep.contains("siliconflow")) return true;
        String ml = provider.getModelListJson() != null ? provider.getModelListJson().toLowerCase() : "";
        // BAAI 系模型通常走 SiliconFlow
        if (ml.contains("bge") && ep.isEmpty()) return true;
        return false;
    }

    @Override
    public float[] embed(String text, String model, AiProvider provider) throws Exception {
        String apiKey = resolveApiKey(provider.getApiKeyRef());
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("SiliconFlow apiKey missing");
        }
        String base = provider.getEndpoint() != null && !provider.getEndpoint().isBlank()
                ? provider.getEndpoint().trim()
                : "https://api.siliconflow.cn/v1";
        String url = normalizeUrl(base);
        String effectiveModel = (model != null && !model.isBlank()) ? model : resolveDefaultModel(provider);
        if (effectiveModel == null || effectiveModel.isBlank()) effectiveModel = "BAAI/bge-m3";

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", effectiveModel);
        body.put("input", text);
        // some providers accept encoding_format float
        String jsonBody = objectMapper.writeValueAsString(body);

        log.debug("[SF Embedding] url={}, model={}, textLen={}", url, effectiveModel, text.length());

        String resp = webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(TIMEOUT);

        if (resp == null || resp.isBlank()) throw new IllegalStateException("empty response");
        JsonNode root = objectMapper.readTree(resp);
        JsonNode data = root.path("data");
        if (!data.isArray() || data.isEmpty()) throw new IllegalStateException("no data in response: " + resp.substring(0, Math.min(500, resp.length())));
        JsonNode embeddingNode = data.get(0).path("embedding");
        if (!embeddingNode.isArray()) throw new IllegalStateException("no embedding array");
        float[] vec = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) vec[i] = (float) embeddingNode.get(i).asDouble();
        return l2Normalize(vec);
    }

    private String normalizeUrl(String base) {
        if (base.endsWith("/embeddings")) return base;
        if (base.endsWith("/v1")) return base + "/embeddings";
        if (base.endsWith("/")) return base + "v1/embeddings";
        if (base.contains("/v1/embeddings")) return base;
        // try append
        if (!base.contains("/embeddings")) {
            if (base.endsWith("/v1") || base.endsWith("/v1/")) return base.replaceAll("/+$", "") + "/embeddings";
            return base.replaceAll("/+$", "") + "/v1/embeddings";
        }
        return base;
    }

    private String resolveApiKey(String ref) {
        if (ref == null || ref.isBlank()) return "";
        String t = ref.trim();
        if (t.startsWith("{")) {
            try {
                JsonNode n = objectMapper.readTree(t);
                JsonNode k = n.get("apiKey");
                if (k == null) k = n.get("api_key");
                if (k == null) k = n.get("token");
                return k == null ? "" : k.asText("");
            } catch (Exception e) {
                return t;
            }
        }
        return t;
    }

    private String resolveDefaultModel(AiProvider provider) {
        String ml = provider.getModelListJson();
        if (ml == null || ml.isBlank()) return null;
        try {
            JsonNode arr = objectMapper.readTree(ml);
            if (arr.isArray() && arr.size() > 0) {
                JsonNode code = arr.get(0).get("code");
                return code == null ? null : code.asText();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private float[] l2Normalize(float[] v) {
        double norm = 0;
        for (float f : v) norm += (double) f * f;
        norm = Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
        return v;
    }
}
