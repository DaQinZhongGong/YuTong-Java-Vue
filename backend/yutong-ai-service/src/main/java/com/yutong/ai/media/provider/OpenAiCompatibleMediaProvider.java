package com.yutong.ai.media.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yutong.ai.chat.service.llm.LlmProviderSelector;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.service.AiProviderRegistry;
import com.yutong.ai.media.domain.MediaJob;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.system.file.domain.SysFile;
import com.yutong.system.file.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

/**
 * OpenAI 兼容多模态：image → /images/generations，audio → /audio/speech。
 * 视频/PPT 走明确失败而非假 URL。产物上传 MinIO。
 */
@Component
public class OpenAiCompatibleMediaProvider implements MediaProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleMediaProvider.class);

    private final AiProviderRegistry providerRegistry;
    private final LlmProviderSelector providerSelector;
    private final FileService fileService;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleMediaProvider(AiProviderRegistry providerRegistry,
                                         LlmProviderSelector providerSelector,
                                         FileService fileService,
                                         ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.providerSelector = providerSelector;
        this.fileService = fileService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerCode() {
        return "openai-compatible";
    }

    @Override
    public boolean supports(String mediaType) {
        return MediaJob.TYPE_IMAGE.equals(mediaType) || MediaJob.TYPE_AUDIO.equals(mediaType);
    }

    @Override
    public GenerateResult generate(MediaJob job) {
        String type = job.getMediaType();
        if (MediaJob.TYPE_VIDEO.equals(type) || MediaJob.TYPE_PPT.equals(type)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "当前未配置可用的 " + type + " 供应商。请在供应商管理中登记 model_type=" + type + " 的端点后再生成。");
        }
        AiProvider provider = resolveProvider(type, job.getProviderCode());
        String apiKey = providerSelector.resolveApiKey(provider.getApiKeyRef());
        String endpoint = provider.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "供应商 endpoint 不能为空: " + provider.getProviderCode());
        }
        String model = firstNonBlank(job.getModelCode(),
                providerSelector.extractDefaultModel(provider.getModelListJson()),
                MediaJob.TYPE_IMAGE.equals(type) ? "dall-e-3" : "tts-1");
        try {
            if (MediaJob.TYPE_IMAGE.equals(type)) {
                return generateImage(provider, apiKey, endpoint, model, job);
            }
            return generateSpeech(provider, apiKey, endpoint, model, job);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("media generate failed: type={} provider={}", type, provider.getProviderCode(), e);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "媒体生成失败: " + e.getMessage());
        }
    }

    private GenerateResult generateImage(AiProvider provider, String apiKey, String endpoint, String model, MediaJob job)
            throws Exception {
        String size = extractJsonField(job.getInputJson(), "size", "1024x1024");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("prompt", job.getPrompt());
        body.put("size", size);
        body.put("n", 1);
        body.put("response_format", "b64_json");
        HttpResponse<String> resp = postJson(join(endpoint, "/images/generations"), apiKey, body.toString(),
                resolveTimeout(provider));
        if (resp.statusCode() / 100 != 2) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                    "图像生成 HTTP " + resp.statusCode() + ": " + truncate(resp.body(), 300));
        }
        JsonNode root = objectMapper.readTree(resp.body());
        JsonNode data0 = root.path("data").path(0);
        byte[] bytes;
        String b64 = data0.path("b64_json").asText(null);
        if (b64 != null && !b64.isBlank()) {
            bytes = Base64.getDecoder().decode(b64);
        } else {
            String url = data0.path("url").asText(null);
            if (url == null || url.isBlank()) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "图像生成未返回 b64_json 或 url");
            }
            bytes = downloadBytes(url, resolveTimeout(provider));
        }
        SysFile file = fileService.uploadBytes("media-" + job.getId() + ".png", bytes, "image/png");
        String downloadUrl = fileService.getDownloadUrl(file.getId());
        String outputJson = objectMapper.writeValueAsString(java.util.Map.of(
                "fileId", file.getId(),
                "provider", provider.getProviderCode(),
                "model", model,
                "bytes", bytes.length
        ));
        return new GenerateResult(downloadUrl, outputJson, BigDecimal.ZERO);
    }

    private GenerateResult generateSpeech(AiProvider provider, String apiKey, String endpoint, String model, MediaJob job)
            throws Exception {
        String voice = extractJsonField(job.getInputJson(), "voice", "alloy");
        String format = extractJsonField(job.getInputJson(), "responseFormat", "mp3");
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", model);
        body.put("input", job.getPrompt());
        body.put("voice", voice);
        body.put("response_format", format);
        HttpResponse<byte[]> resp = postBytes(join(endpoint, "/audio/speech"), apiKey, body.toString(),
                resolveTimeout(provider));
        if (resp.statusCode() / 100 != 2) {
            String err = resp.body() == null ? "" : new String(resp.body());
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR,
                    "语音生成 HTTP " + resp.statusCode() + ": " + truncate(err, 300));
        }
        byte[] bytes = resp.body() == null ? new byte[0] : resp.body();
        if (bytes.length == 0) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "语音生成返回空音频");
        }
        String ext = "wav".equalsIgnoreCase(format) ? "wav" : "mp3";
        String mime = "wav".equals(ext) ? "audio/wav" : "audio/mpeg";
        SysFile file = fileService.uploadBytes("media-" + job.getId() + "." + ext, bytes, mime);
        String downloadUrl = fileService.getDownloadUrl(file.getId());
        String outputJson = objectMapper.writeValueAsString(java.util.Map.of(
                "fileId", file.getId(),
                "provider", provider.getProviderCode(),
                "model", model,
                "voice", voice,
                "bytes", bytes.length
        ));
        return new GenerateResult(downloadUrl, outputJson, BigDecimal.ZERO);
    }

    private AiProvider resolveProvider(String mediaType, String requestedCode) {
        if (requestedCode != null && !requestedCode.isBlank() && !"mock".equalsIgnoreCase(requestedCode)) {
            Optional<AiProvider> exact = providerRegistry.listAllEnabled().stream()
                    .filter(p -> requestedCode.equalsIgnoreCase(p.getProviderCode()))
                    .findFirst();
            if (exact.isPresent()) {
                return exact.get();
            }
        }
        var byCap = providerRegistry.resolveByCapability(mediaType, mediaType);
        if (!byCap.isEmpty()) {
            return byCap.get(0);
        }
        Optional<AiProvider> primary = providerRegistry.selectPrimary(null, mediaType);
        if (primary.isPresent()) {
            return primary.get();
        }
        throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                "未找到可用的 " + mediaType + " 供应商。请在供应商管理中配置 model_type=" + mediaType + " 或 multimodal_capabilities。");
    }

    private HttpResponse<String> postJson(String url, String apiKey, String body, Duration timeout) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null && !apiKey.isBlank()) {
            b.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<byte[]> postBytes(String url, String apiKey, String body, Duration timeout) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(timeout)
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (apiKey != null && !apiKey.isBlank()) {
            b.header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    private byte[] downloadBytes(String url, Duration timeout) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(timeout).build();
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(timeout).GET().build();
        HttpResponse<byte[]> resp = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2 || resp.body() == null) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "下载生成图像失败 HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    private String join(String endpoint, String path) {
        String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        if (base.endsWith("/v1") && path.startsWith("/v1/")) {
            return base + path.substring(3);
        }
        return base + path;
    }

    private Duration resolveTimeout(AiProvider provider) {
        int ms = provider.getTimeoutMs() == null || provider.getTimeoutMs() <= 0 ? 60_000 : provider.getTimeoutMs();
        return Duration.ofMillis(Math.min(Math.max(ms, 5_000), 180_000));
    }

    private String extractJsonField(String json, String key, String fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            JsonNode n = objectMapper.readTree(json).get(key);
            if (n == null || n.isNull() || n.asText().isBlank()) {
                return fallback;
            }
            return n.asText();
        } catch (Exception e) {
            return fallback;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
