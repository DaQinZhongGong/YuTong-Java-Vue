package com.yutong.ai.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.service.AiProviderRegistry;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.service.embedding.MultimodalEmbeddingProvider;
import com.yutong.ai.rag.service.embedding.RemoteEmbeddingProvider;
import com.yutong.common.auth.CurrentUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 向量化服务 — 本地哈希兜底 + 远程 HTTP 调用动态路由。
 * 设计来源: 13-AI能力设计 RAG 向量检索 / P0-3 Embedding 真远程
 * <p>
 * v0.9 回退路径: 确定性哈希词袋 1536 维向量（无外部依赖）。
 * P1-1 增强: 按 kb.embeddingModel/providerType 通过 AiProviderRegistry 选型，WebClient 5s 超时调用 SiliconFlow/ZhiPu，失败降级哈希并日志。
 * V061 多模态: embedMultimodal 优先 MultimodalEmbeddingProvider（图像/视频 fail-closed）；维度由 kb.embeddingDimension 驱动（默认 1536）。
 * 约束: 保持事务/租户/DataScope 隔离；复用 ai_provider.model_config_json (config_json + modelListJson)；不落明文密钥。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);
    private static final int DIMENSION = 1536;
    private static final int MAX_PREVIEW = 8;

    private final AiProviderRegistry providerRegistry;
    private final List<RemoteEmbeddingProvider> remoteProviders;
    private final List<MultimodalEmbeddingProvider> multimodalProviders;
    private final ObjectMapper objectMapper;

    public EmbeddingService(AiProviderRegistry providerRegistry,
                            List<RemoteEmbeddingProvider> remoteProviders,
                            List<MultimodalEmbeddingProvider> multimodalProviders,
                            ObjectMapper objectMapper) {
        this.providerRegistry = providerRegistry;
        this.remoteProviders = remoteProviders != null ? remoteProviders : List.of();
        this.multimodalProviders = multimodalProviders != null ? multimodalProviders : List.of();
        this.objectMapper = objectMapper;
    }

    // For unit tests that instantiate via no-arg
    public EmbeddingService() {
        this.providerRegistry = null;
        this.remoteProviders = List.of();
        this.multimodalProviders = List.of();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 多模态/文本向量化结果（用于测试端点回报 remoteSuccess）。
     */
    public record MultimodalEmbedOutcome(float[] vector, boolean remoteSuccess, String message) {
    }

    /**
     * 将文本转换为向量 — 兼容旧调用（无 KB 上下文时直接哈希 + 尝试通用远程）。
     */
    public float[] embed(String text) {
        return embedInternal(text, null);
    }

    /**
     * KB 感知的向量化 — 优先使用 kb.embeddingModel 对应的远端供应商，失败降级哈希。
     */
    public float[] embed(String text, AiKnowledgeBase kb) {
        return embedInternal(text, kb);
    }

    /**
     * 多模态向量化入口。
     * <ul>
     *   <li>text → 兼容既有文本路径（远程优先，哈希兜底）</li>
     *   <li>image / video → 优先 MultimodalEmbeddingProvider，失败关闭（抛异常，不返回假向量）</li>
     * </ul>
     * 维度: kb.embeddingDimension（默认 1536），与远端不一致时 adapt 并 warn。
     */
    public float[] embedMultimodal(String modality, String payload, AiKnowledgeBase kb) {
        MultimodalEmbedOutcome outcome = embedMultimodalOutcome(modality, payload, kb);
        String m = normalizeModality(modality);
        if (!"text".equals(m) && !outcome.remoteSuccess()) {
            throw new IllegalStateException(outcome.message() != null
                    ? outcome.message()
                    : "Multimodal embedding failed for modality=" + m);
        }
        return outcome.vector();
    }

    /**
     * 带状态的多模态向量化 — 文本允许哈希兜底；图像/视频失败时 remoteSuccess=false（调用方决定是否抛错）。
     */
    public MultimodalEmbedOutcome embedMultimodalOutcome(String modality, String payload, AiKnowledgeBase kb) {
        String m = normalizeModality(modality);
        int targetDim = resolveTargetDimension(kb);

        if ("text".equals(m)) {
            DetailedEmbed detailed = embedInternalDetailed(payload, kb);
            return new MultimodalEmbedOutcome(detailed.vector(), detailed.remoteSuccess(),
                    detailed.remoteSuccess() ? "remote" : "hash-fallback");
        }

        if (payload == null || payload.isBlank()) {
            return new MultimodalEmbedOutcome(new float[targetDim], false, "payload blank");
        }
        if (providerRegistry == null || multimodalProviders.isEmpty()) {
            return new MultimodalEmbedOutcome(null, false,
                    "Multimodal embedding provider unavailable for modality=" + m);
        }

        try {
            float[] vec = tryMultimodalRemote(m, payload, kb);
            if (vec == null || vec.length == 0) {
                return new MultimodalEmbedOutcome(null, false,
                        "No multimodal provider succeeded for modality=" + m);
            }
            if (vec.length != targetDim) {
                log.warn("[Embedding] multimodal dimension mismatch expected={}, actual={}, modality={}, kbId={}",
                        targetDim, vec.length, m, kb != null ? kb.getId() : "null");
                vec = adaptDimension(vec, targetDim);
            }
            return new MultimodalEmbedOutcome(vec, true, "remote");
        } catch (Exception e) {
            log.warn("[Embedding] multimodal remote failed modality={}, kbId={}, err={}",
                    m, kb != null ? kb.getId() : "null", e.getMessage());
            return new MultimodalEmbedOutcome(null, false, e.getMessage());
        }
    }

    private record DetailedEmbed(float[] vector, boolean remoteSuccess) {
    }

    private String normalizeModality(String modality) {
        return (modality == null || modality.isBlank()) ? "text" : modality.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 解析目标向量维度: kb.embeddingDimension 有效则用之，否则默认 1536。
     */
    public int resolveTargetDimension(AiKnowledgeBase kb) {
        if (kb != null && kb.getEmbeddingDimension() != null && kb.getEmbeddingDimension() > 0
                && kb.getEmbeddingDimension() <= 8192) {
            return kb.getEmbeddingDimension();
        }
        return DIMENSION;
    }

    private float[] embedInternal(String text, AiKnowledgeBase kb) {
        return embedInternalDetailed(text, kb).vector();
    }

    private DetailedEmbed embedInternalDetailed(String text, AiKnowledgeBase kb) {
        int targetDim = resolveTargetDimension(kb);
        if (text == null || text.isBlank()) {
            return new DetailedEmbed(new float[targetDim], false);
        }
        // 尝试远程
        if (providerRegistry != null && !remoteProviders.isEmpty()) {
            try {
                float[] remote = tryRemote(text, kb);
                if (remote != null) {
                    if (remote.length != targetDim) {
                        log.warn("[Embedding] remote dimension mismatch expected={}, actual={}, model={}, kbId={}",
                                targetDim, remote.length,
                                kb != null ? kb.getEmbeddingModel() : "null",
                                kb != null ? kb.getId() : "null");
                        remote = adaptDimension(remote, targetDim);
                    }
                    log.debug("[Embedding] remote success textLen={}, dim={}, kbId={}",
                            text.length(), remote.length, kb != null ? kb.getId() : "null");
                    return new DetailedEmbed(remote, true);
                }
            } catch (Exception e) {
                log.warn("[Embedding] remote failed, fallback to hash textLen={}, kbId={}, err={}",
                        text.length(), kb != null ? kb.getId() : "null", e.getMessage());
            }
        }
        return new DetailedEmbed(hashEmbed(text, targetDim), false);
    }

    private float[] tryRemote(String text, AiKnowledgeBase kb) throws Exception {
        String tenantId = CurrentUserContext.getTenantId();
        // kb 为空时尝试通用 vector 供应商
        String kbModel = kb != null ? kb.getEmbeddingModel() : null;
        String hintProviderType = inferProviderType(kbModel);
        List<AiProvider> candidates;
        if (hintProviderType != null) {
            candidates = tenantId != null ? providerRegistry.resolveForTenant(tenantId, hintProviderType, "vector") : List.of();
            // 若按 hint 未命中，回退到通用 vector 列表
            if (candidates.isEmpty()) {
                candidates = tenantId != null ? providerRegistry.resolveForTenant(tenantId, null, "vector") : List.of();
            }
        } else {
            candidates = tenantId != null ? providerRegistry.resolveForTenant(tenantId, null, "vector") : List.of();
        }
        if (candidates.isEmpty()) return null;

        // 按健康度/优先级已排序，逐个尝试直到成功
        for (AiProvider provider : candidates) {
            RemoteEmbeddingProvider impl = findSupportingProvider(provider);
            if (impl == null) continue;
            // 复用 ai_provider 的 model_config_json (config_json) 与 modelListJson：若 config_json 包含 embeddingModel 覆盖则优先
            String model = resolveModelForProvider(kbModel, provider);
            try {
                float[] vec = impl.embed(text, model, provider);
                if (vec != null && vec.length > 0) return vec;
            } catch (Exception e) {
                log.warn("[Embedding] provider {} ({}) failed, try next: {}", provider.getProviderCode(), provider.getProviderType(), e.getMessage());
                // continue to next provider
            }
        }
        return null;
    }

    private float[] tryMultimodalRemote(String modality, String payload, AiKnowledgeBase kb) throws Exception {
        String tenantId = CurrentUserContext.getTenantId();
        String kbModel = kb != null ? kb.getEmbeddingModel() : null;
        List<AiProvider> candidates = tenantId != null
                ? providerRegistry.resolveForTenant(tenantId, null, "vector")
                : List.of();
        if (candidates.isEmpty()) {
            // 多模态供应商可能挂在 image/video 模型类型上
            candidates = tenantId != null
                    ? providerRegistry.resolveForTenant(tenantId, null, "image")
                    : List.of();
        }
        if (candidates.isEmpty()) return null;

        for (AiProvider provider : candidates) {
            MultimodalEmbeddingProvider impl = findMultimodalProvider(provider, modality);
            if (impl == null) continue;
            String model = resolveModelForProvider(kbModel, provider);
            try {
                float[] vec = invokeMultimodal(impl, modality, payload, model, provider);
                if (vec != null && vec.length > 0) return vec;
            } catch (Exception e) {
                log.warn("[Embedding] multimodal provider {} ({}) failed, try next: {}",
                        provider.getProviderCode(), provider.getProviderType(), e.getMessage());
            }
        }
        return null;
    }

    private float[] invokeMultimodal(MultimodalEmbeddingProvider impl, String modality,
                                     String payload, String model, AiProvider provider) throws Exception {
        return switch (modality) {
            case "image" -> impl.embedImage(payload, model, provider);
            case "video" -> impl.embedVideo(payload, model, provider);
            case "audio" -> throw new IllegalStateException("audio modality not supported by " + impl.getProviderType());
            default -> impl.embed(payload, model, provider);
        };
    }

    private MultimodalEmbeddingProvider findMultimodalProvider(AiProvider provider, String modality) {
        for (MultimodalEmbeddingProvider p : multimodalProviders) {
            try {
                if (p.supportsModality(modality) && p.supports(provider)) return p;
            } catch (Exception ignored) {
            }
        }
        String pt = provider.getProviderType() != null ? provider.getProviderType().toLowerCase(Locale.ROOT) : "";
        for (MultimodalEmbeddingProvider p : multimodalProviders) {
            try {
                if (p.supportsModality(modality)
                        && ("bailian".equalsIgnoreCase(p.getProviderType())
                        || "qianwen".equals(pt) || "aliyun".equals(pt)
                        || "bailian".equals(pt) || "alibailian".equals(pt)
                        || "dashscope".equals(pt))) {
                    return p;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String inferProviderType(String model) {
        if (model == null || model.isBlank()) return null;
        String m = model.toLowerCase();
        // zhipu 特征: embedding-3 / zhipu / glm
        if (m.contains("zhipu") || m.contains("embedding-3") || m.contains("glm-embedding")) return "zhipu";
        // siliconflow 特征: bge / silicon / BAAI
        if (m.contains("silicon") || m.contains("bge") || m.contains("baai")) return "siliconflow";
        // alia/百炼 multimodal-embedding-v1 → bailian
        if (m.contains("multimodal-embedding") || m.contains("tongyi") || m.contains("qwen-embedding")) return "bailian";
        return null;
    }

    private RemoteEmbeddingProvider findSupportingProvider(AiProvider provider) {
        for (RemoteEmbeddingProvider p : remoteProviders) {
            try {
                if (p.supports(provider)) return p;
            } catch (Exception ignored) {}
        }
        // 兜底：若 providerType 恰好匹配某个 impl 的 getProviderType
        String pt = provider.getProviderType() != null ? provider.getProviderType().toLowerCase() : "";
        for (RemoteEmbeddingProvider p : remoteProviders) {
            if (pt.equalsIgnoreCase(p.getProviderType())) return p;
        }
        return null;
    }

    private String resolveModelForProvider(String kbModel, AiProvider provider) {
        if (kbModel != null && !kbModel.isBlank()) return kbModel.trim();
        // 复用 ai_provider.config_json 中的 embeddingModel 覆盖（兼容 业界同类实现 扩展）
        String cfg = provider.getConfigJson();
        if (cfg != null && !cfg.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(cfg);
                JsonNode m = node.get("embeddingModel");
                if (m == null) m = node.get("embedding_model");
                if (m == null) m = node.get("model");
                if (m != null && !m.asText().isBlank()) return m.asText().trim();
            } catch (Exception ignored) {}
        }
        // 回退到 modelListJson 首个模型
        String ml = provider.getModelListJson();
        if (ml != null && !ml.isBlank()) {
            try {
                JsonNode arr = objectMapper.readTree(ml);
                if (arr.isArray() && arr.size() > 0) {
                    JsonNode code = arr.get(0).get("code");
                    if (code != null && !code.asText().isBlank()) return code.asText().trim();
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private float[] adaptDimension(float[] vec, int target) {
        if (vec.length == target) return vec;
        float[] out = new float[target];
        if (vec.length > target) {
            System.arraycopy(vec, 0, out, 0, target);
            // re-normalize
            double norm = 0;
            for (float v : out) norm += (double) v * v;
            norm = Math.sqrt(norm);
            if (norm > 0) for (int i = 0; i < out.length; i++) out[i] /= (float) norm;
            return out;
        } else {
            System.arraycopy(vec, 0, out, 0, vec.length);
            // pad remaining 0, re-normalize already normalized; just return
            return out;
        }
    }

    // ===== 哈希 fallback（保留原有实现，维度可配） =====

    private float[] hashEmbed(String text, int dimension) {
        float[] vector = new float[dimension];
        String[] tokens = tokenize(text);
        for (String token : tokens) {
            if (token.isEmpty()) continue;
            int hash = stableHash(token);
            int index = Math.abs(hash % dimension);
            vector[index] += (hash >= 0) ? 1.0f : -1.0f;
        }
        float norm = 0;
        for (float v : vector) norm += v * v;
        norm = (float) Math.sqrt(norm);
        if (norm > 0) for (int i = 0; i < dimension; i++) vector[i] /= norm;
        return vector;
    }

    public String toPgVectorFormat(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    public int getDimension() {
        return DIMENSION;
    }

    /**
     * 前 N 维预览（测试端点用，避免返回完整向量）。
     */
    public List<Float> preview(float[] vector, int max) {
        if (vector == null) return List.of();
        int n = Math.min(max > 0 ? max : MAX_PREVIEW, vector.length);
        java.util.ArrayList<Float> out = new java.util.ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(vector[i]);
        return out;
    }

    private String[] tokenize(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        String[] words = lower.split("[^a-z0-9\u4e00-\u9fa5]+");
        var result = new java.util.ArrayList<String>();
        for (String word : words) {
            if (word.isEmpty()) continue;
            boolean hasCJK = false;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (c >= '\u4e00' && c <= '\u9fa5') {
                    result.add(String.valueOf(c));
                    hasCJK = true;
                }
            }
            if (!hasCJK) result.add(word);
        }
        return result.toArray(new String[0]);
    }

    private int stableHash(String s) {
        int hash = 17;
        for (int i = 0; i < s.length(); i++) hash = hash * 31 + s.charAt(i);
        return hash;
    }
}
