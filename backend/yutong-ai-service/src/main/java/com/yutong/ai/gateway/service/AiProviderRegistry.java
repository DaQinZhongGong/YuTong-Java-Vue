package com.yutong.ai.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiModelType;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.domain.AiProviderType;
import com.yutong.ai.gateway.mapper.AiProviderMapper;
import com.yutong.common.auth.CurrentUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 统一供应商注册表：按 provider_type + model_type 路由，优先级 + 健康度 Fallback。
 * 设计来源: V036 平价能力、13-AI能力设计、P6-02 免费 LLM 供应商集成
 *
 * <p>路由规则:
 * <ol>
 *   <li>仅选择 enabled=true 且 deleted=false 的供应商</li>
 *   <li>按 providerType / modelType 精确匹配过滤（null/blank 表示不限制该维度）</li>
 *   <li>健康度排序: HEALTHY (0) &lt; UNKNOWN (1) &lt; DEGRADED (2) &lt; UNHEALTHY (3)；UNHEALTHY 仅在无其他可用时 fallback</li>
 *   <li>同健康度按 priority 升序（越小越高），再按 createdTime 升序稳定排序</li>
 *   <li>多模态路由: 当请求需要 image/video 等能力时，检查 multimodal_capabilities 或 model_type_list_json 是否声明支持</li>
 * </ol>
 *
 * <p>生产级约束: 配置 via DB + UI 实时生效，无需重启；本类无本地缓存，直接查 DB 保证实时性，
 * 上层可按需叠加短 TTL 缓存。
 */
@Service
public class AiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private static final Set<String> UNHEALTHY_STATUSES = Set.of("UNHEALTHY");

    private final AiProviderMapper providerMapper;
    private final ObjectMapper objectMapper;

    public AiProviderRegistry(AiProviderMapper providerMapper, ObjectMapper objectMapper) {
        this.providerMapper = providerMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 按 providerType + modelType 解析可用供应商列表，已按健康度+优先级排序。
     *
     * @param providerType 供应商类型 code，null/blank 表示不限
     * @param modelType    模型类型 code，null/blank 表示不限
     * @return 排序后的供应商列表（可能为空）
     */
    public List<AiProvider> resolve(String providerType, String modelType) {
        String tenantId = CurrentUserContext.getTenantId();
        return resolveForTenant(tenantId, providerType, modelType);
    }

    /**
     * 指定租户的路由（便于任务/健康检查等非请求上下文调用）。
     */
    public List<AiProvider> resolveForTenant(String tenantId, String providerType, String modelType) {
        LambdaQueryWrapper<AiProvider> wrapper = new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getTenantId, tenantId)
                .eq(AiProvider::getEnabled, true)
                .eq(providerType != null && !providerType.isBlank(), AiProvider::getProviderType, providerType.trim().toLowerCase())
                .eq(modelType != null && !modelType.isBlank(), AiProvider::getModelType, modelType.trim().toLowerCase())
                .orderByAsc(AiProvider::getPriority)
                .orderByAsc(AiProvider::getCreatedTime);

        List<AiProvider> candidates = providerMapper.selectList(wrapper);
        if (candidates.isEmpty()) {
            return List.of();
        }
        return sortByHealthAndPriority(candidates);
    }

    /**
     * 选择单个最优供应商（健康度+优先级最高者），UNHEALTHY 仅在无其他时返回。
     */
    public Optional<AiProvider> selectPrimary(String providerType, String modelType) {
        List<AiProvider> sorted = resolve(providerType, modelType);
        if (sorted.isEmpty()) {
            return Optional.empty();
        }
        List<AiProvider> healthy = filterUnhealthy(sorted);
        if (!healthy.isEmpty()) {
            return Optional.of(healthy.get(0));
        }
        log.warn("all providers unhealthy, fallback to first unhealthy: providerType={} modelType={} count={}",
                providerType, modelType, sorted.size());
        return Optional.of(sorted.get(0));
    }

    /**
     * 多模态路由：按所需能力（如 image/video/audio/ppt）筛选供应商。
     *
     * @param requiredCapability 如 "image" / "video" / "audio" / "ppt"
     * @param modelType          可选主类型过滤
     * @return 支持该能力的供应商列表
     */
    public List<AiProvider> resolveByCapability(String requiredCapability, String modelType) {
        if (requiredCapability == null || requiredCapability.isBlank()) {
            return resolve(null, modelType);
        }
        String tenantId = CurrentUserContext.getTenantId();
        List<AiProvider> candidates = resolveForTenant(tenantId, null, modelType);
        List<AiProvider> matched = new ArrayList<>();
        String cap = requiredCapability.trim().toLowerCase();
        for (AiProvider p : candidates) {
            if (supportsCapability(p, cap)) {
                matched.add(p);
            }
        }
        return matched;
    }

    /**
     * 列出当前租户所有可用供应商，按健康度+优先级排序（用于管理端展示与健康检查）。
     */
    public List<AiProvider> listAllEnabled() {
        String tenantId = CurrentUserContext.getTenantId();
        return resolveForTenant(tenantId, null, null);
    }

    /**
     * 校验 providerType/modelType 枚举合法性，非严格模式仅日志警告。
     */
    public boolean isProviderTypeValid(String providerType) {
        return providerType == null || providerType.isBlank() || AiProviderType.isValid(providerType);
    }

    public boolean isModelTypeValid(String modelType) {
        return modelType == null || modelType.isBlank() || AiModelType.isValid(modelType);
    }

    // ===== 内部：排序与能力判断 =====

    private List<AiProvider> sortByHealthAndPriority(List<AiProvider> providers) {
        List<AiProvider> sorted = new ArrayList<>(providers);
        sorted.sort(Comparator
                .comparingInt((AiProvider p) -> healthRank(p.getHealthStatus()))
                .thenComparing(p -> p.getPriority() == null ? Integer.MAX_VALUE : p.getPriority())
                .thenComparing(p -> p.getCreatedTime() == null ? null : p.getCreatedTime(),
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return sorted;
    }

    private List<AiProvider> filterUnhealthy(List<AiProvider> sorted) {
        List<AiProvider> out = new ArrayList<>();
        for (AiProvider p : sorted) {
            String hs = p.getHealthStatus();
            if (hs == null || !UNHEALTHY_STATUSES.contains(hs.toUpperCase())) {
                out.add(p);
            }
        }
        return out;
    }

    private int healthRank(String healthStatus) {
        if (healthStatus == null || healthStatus.isBlank()) {
            return 1; // UNKNOWN
        }
        return switch (healthStatus.trim().toUpperCase()) {
            case "HEALTHY" -> 0;
            case "UNKNOWN" -> 1;
            case "DEGRADED" -> 2;
            case "UNHEALTHY" -> 3;
            default -> 1;
        };
    }

    private boolean supportsCapability(AiProvider provider, String capability) {
        // 1) 优先检查 multimodal_capabilities JSON: {"image":true,...}
        String mmJson = provider.getMultimodalCapabilities();
        if (mmJson != null && !mmJson.isBlank()) {
            try {
                Map<String, Object> map = objectMapper.readValue(mmJson, new TypeReference<Map<String, Object>>() {});
                Object v = map.get(capability);
                if (v instanceof Boolean b) {
                    if (b) return true;
                } else if (v != null) {
                    String s = v.toString().trim().toLowerCase();
                    if ("true".equals(s) || "1".equals(s) || "yes".equals(s)) return true;
                }
                // 若该 key 明确声明 false，则不匹配
                if (map.containsKey(capability)) {
                    return false;
                }
            } catch (Exception e) {
                log.debug("multimodal_capabilities parse failed: providerCode={} err={}",
                        provider.getProviderCode(), e.getMessage());
            }
        }
        // 2) 回退检查 model_type_list_json: ["chat","image"]
        String listJson = provider.getModelTypeListJson();
        if (listJson != null && !listJson.isBlank()) {
            try {
                List<String> list = objectMapper.readValue(listJson, new TypeReference<List<String>>() {});
                for (String s : list) {
                    if (capability.equalsIgnoreCase(s)) {
                        return true;
                    }
                }
            } catch (Exception e) {
                log.debug("model_type_list_json parse failed: providerCode={} err={}",
                        provider.getProviderCode(), e.getMessage());
            }
        }
        // 3) 最后检查主 model_type 是否即所需能力
        String mainType = provider.getModelType();
        return capability.equalsIgnoreCase(mainType);
    }
}
