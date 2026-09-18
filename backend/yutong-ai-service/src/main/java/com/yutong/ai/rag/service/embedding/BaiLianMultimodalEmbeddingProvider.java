package com.yutong.ai.rag.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yutong.ai.gateway.domain.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/**
 * 阿里百炼 (DashScope) 多模态 Embedding 供应商。
 * 设计来源: docs/compose/spec/ai-depth-parity.md S2.3
 * API: POST {base}/services/embeddings/multimodal-embedding
 * <p>
 * providerType 匹配 bailian / qianwen / aliyun / alibailian；
 * endpoint 可含 dashscope / bailian / aliyuncs。
 * 约束: 无 Key 或调用失败直接抛异常（fail-closed），复用 WebClient 5s→15s 超时风格，不落明文密钥。
 */
@Component
public class BaiLianMultimodalEmbeddingProvider implements MultimodalEmbeddingProvider {

    private static final Logger log = LoggerFactory.getLogger(BaiLianMultimodalEmbeddingProvider.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final String DEFAULT_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1";
    private static final String DEFAULT_MODEL = "multimodal-embedding-v1";
    private static final Set<String> SUPPORTED_PROVIDER_TYPES = Set.of(
            "bailian", "qianwen", "aliyun", "alibailian", "dashscope");
    private static final Set<String> SUPPORTED_MODALITIES = Set.of("text", "image", "video");

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public BaiLianMultimodalEmbeddingProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                        HttpClient.create().responseTimeout(TIMEOUT)))
                .build();
    }

    @Override
    public String getProviderType() {
        return "bailian";
    }

    @Override
    public boolean supports(AiProvider provider) {
        if (provider == null) return false;
        String pt = provider.getProviderType();
        if (pt != null && SUPPORTED_PROVIDER_TYPES.contains(pt.trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        String ep = provider.getEndpoint() != null ? provider.getEndpoint().toLowerCase(Locale.ROOT) : "";
        return ep.contains("dashscope") || ep.contains("bailian") || ep.contains("aliyuncs");
    }

    @Override
    public boolean supportsModality(String modality) {
        if (modality == null || modality.isBlank()) return false;
        return SUPPORTED_MODALITIES.contains(modality.trim().toLowerCase(Locale.ROOT));
    }

    @Override
    public float[] embed(String text, String model, AiProvider provider) throws Exception {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("text payload required");
        }
        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode texts = objectMapper.createArrayNode();
        texts.add(text);
        input.set("texts", texts);
        return callApi(input, model, provider, "text");
    }

    @Override
    public float[] embedImage(String imageUrlOrDataUrl, String model, AiProvider provider) throws Exception {
        if (imageUrlOrDataUrl == null || imageUrlOrDataUrl.isBlank()) {
            throw new IllegalArgumentException("image payload required");
        }
        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode images = objectMapper.createArrayNode();
        images.add(imageUrlOrDataUrl);
        input.set("images", images);
        return callApi(input, model, provider, "image");
    }

    @Override
    public float[] embedVideo(String videoUrl, String model, AiProvider provider) throws Exception {
        if (videoUrl == null || videoUrl.isBlank()) {
            throw new IllegalArgumentException("video payload required");
        }
        ObjectNode input = objectMapper.createObjectNode();
        ArrayNode videos = objectMapper.createArrayNode();
        videos.add(videoUrl);
        input.set("videos", videos);
        return callApi(input, model, provider, "video");
    }

    private float[] callApi(ObjectNode input, String model, AiProvider provider, String modality) throws Exception {
        String apiKey = resolveApiKey(provider != null ? provider.getApiKeyRef() : null);
        if (apiKey == null || apiKey.isBlank()) {
            // fail-closed: 无 Key 不做假向量
            throw new IllegalStateException("BaiLian multimodal embedding apiKey missing");
        }
        String url = normalizeUrl(provider != null ? provider.getEndpoint() : null);
        String effectiveModel = (model != null && !model.isBlank()) ? model.trim() : resolveDefaultModel(provider);
        if (effectiveModel == null || effectiveModel.isBlank()) effectiveModel = DEFAULT_MODEL;

        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", effectiveModel);
        body.set("input", input);

        String jsonBody = objectMapper.writeValueAsString(body);
        log.debug("[BaiLian MultiEmbed] url={}, model={}, modality={}", url, effectiveModel, modality);

        String resp = webClient.post()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(jsonBody)
                .retrieve()
                .bodyToMono(String.class)
                .block(TIMEOUT);

        if (resp == null || resp.isBlank()) {
            throw new IllegalStateException("empty response from BaiLian multimodal embedding");
        }
        return parseEmbedding(resp);
    }

    private float[] parseEmbedding(String resp) throws Exception {
        JsonNode root = objectMapper.readTree(resp);
        // DashScope: output.embeddings[].embedding
        JsonNode embeddings = root.path("output").path("embeddings");
        if (embeddings.isArray() && !embeddings.isEmpty()) {
            JsonNode embeddingNode = embeddings.get(0).path("embedding");
            if (embeddingNode.isArray() && embeddingNode.size() > 0) {
                return toFloatArray(embeddingNode);
            }
        }
        // OpenAI-compatible fallback: data[].embedding
        JsonNode data = root.path("data");
        if (data.isArray() && !data.isEmpty()) {
            JsonNode embeddingNode = data.get(0).path("embedding");
            if (embeddingNode.isArray() && embeddingNode.size() > 0) {
                return toFloatArray(embeddingNode);
            }
        }
        String code = root.path("code").asText("");
        String message = root.path("message").asText("");
        throw new IllegalStateException("no embedding in BaiLian response code=" + code
                + " message=" + message
                + " body=" + resp.substring(0, Math.min(400, resp.length())));
    }

    private float[] toFloatArray(JsonNode embeddingNode) {
        float[] vec = new float[embeddingNode.size()];
        for (int i = 0; i < embeddingNode.size(); i++) {
            vec[i] = (float) embeddingNode.get(i).asDouble();
        }
        return l2Normalize(vec);
    }

    private String normalizeUrl(String base) {
        String b = (base == null || base.isBlank()) ? DEFAULT_ENDPOINT : base.trim();
        b = b.replaceAll("/+$", "");
        if (b.endsWith("/multimodal-embedding")) return b;
        if (b.endsWith("/embeddings/multimodal-embedding")) return b;
        if (b.endsWith("/api/v1")) return b + "/services/embeddings/multimodal-embedding";
        if (b.endsWith("/v1")) return b + "/services/embeddings/multimodal-embedding";
        // bare host e.g. https://dashscope.aliyuncs.com
        if (!b.contains("/api/")) return b + "/api/v1/services/embeddings/multimodal-embedding";
        return b + "/services/embeddings/multimodal-embedding";
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
                if (k == null) k = n.get("dashscope_api_key");
                return k == null ? "" : k.asText("");
            } catch (Exception e) {
                return t;
            }
        }
        return t;
    }

    private String resolveDefaultModel(AiProvider provider) {
        if (provider == null) return null;
        String cfg = provider.getConfigJson();
        if (cfg != null && !cfg.isBlank()) {
            try {
                JsonNode n = objectMapper.readTree(cfg);
                JsonNode m = n.get("embeddingModel");
                if (m == null) m = n.get("embedding_model");
                if (m == null) m = n.get("multimodalEmbeddingModel");
                if (m != null && !m.asText().isBlank()) return m.asText().trim();
            } catch (Exception ignored) {
            }
        }
        String ml = provider.getModelListJson();
        if (ml == null || ml.isBlank()) return null;
        try {
            JsonNode arr = objectMapper.readTree(ml);
            if (arr.isArray() && arr.size() > 0) {
                JsonNode code = arr.get(0).get("code");
                return code == null ? null : code.asText();
            }
        } catch (Exception ignored) {
        }
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
