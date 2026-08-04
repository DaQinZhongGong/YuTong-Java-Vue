package com.yutong.ai.chat.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.mapper.AiProviderMapper;
import com.yutong.common.auth.CurrentUserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LLM 供应商选择器。
 * 按优先级从已启用的供应商中选择第一个，并解析其模型列表。
 * 设计来源: P6-02 免费 LLM 供应商集成
 */
@Component
public class LlmProviderSelector {

    private static final Logger log = LoggerFactory.getLogger(LlmProviderSelector.class);

    private final AiProviderMapper providerMapper;
    private final ObjectMapper objectMapper;

    public LlmProviderSelector(AiProviderMapper providerMapper, ObjectMapper objectMapper) {
        this.providerMapper = providerMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查找当前租户启用的 LLM 供应商。
     *
     * @return 可选的供应商及适配器构造参数
     */
    public Optional<ProviderRuntime> selectEnabledProvider() {
        return selectEnabledProviders().stream().findFirst();
    }

    /**
     * 查找当前租户所有启用的非 mock LLM 供应商，按优先级升序排列。
     *
     * @return 供应商运行时列表
     */
    public List<ProviderRuntime> selectEnabledProviders() {
        String tenantId = CurrentUserContext.getTenantId();
        List<AiProvider> providers = providerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getTenantId, tenantId)
                        .eq(AiProvider::getEnabled, true)
                        .orderByAsc(AiProvider::getPriority));

        List<ProviderRuntime> result = new ArrayList<>();
        for (AiProvider provider : providers) {
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                continue;
            }
            // 跳过纯 mock 供应商
            if ("mock-local".equals(provider.getProviderCode())) {
                continue;
            }
            String protocol = provider.getProtocol();
            if (protocol == null || protocol.isBlank()) {
                protocol = OpenAiCompatibleAdapter.PROTOCOL;
            }
            String apiKey = resolveApiKey(provider.getApiKeyRef());
            String defaultModel = extractDefaultModel(provider.getModelListJson());

            if (defaultModel == null || defaultModel.isBlank()) {
                log.warn("供应商缺少可用模型: providerCode={}", provider.getProviderCode());
                continue;
            }

            LlmProviderAdapter adapter = buildAdapter(protocol, provider, apiKey);
            if (adapter != null) {
                result.add(new ProviderRuntime(provider, adapter, defaultModel));
            }
        }
        return result;
    }

    /**
     * 按编码选择当前租户已启用的 LLM 供应商，可指定模型编码。
     *
     * @param providerCode 供应商编码，为空则自动选择
     * @param modelCode    模型编码，为空则使用供应商默认模型
     * @return 可选的供应商及适配器构造参数
     */
    public Optional<ProviderRuntime> selectProvider(String providerCode, String modelCode) {
        if (providerCode == null || providerCode.isBlank()) {
            return selectEnabledProvider();
        }
        String tenantId = CurrentUserContext.getTenantId();
        AiProvider provider = providerMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getTenantId, tenantId)
                        .eq(AiProvider::getProviderCode, providerCode)
                        .eq(AiProvider::getEnabled, true));
        if (provider == null || !Boolean.TRUE.equals(provider.getEnabled())) {
            log.warn("指定供应商未启用或不存在: providerCode={}", providerCode);
            return Optional.empty();
        }
        if ("mock-local".equals(provider.getProviderCode())) {
            return Optional.empty();
        }
        String protocol = provider.getProtocol();
        if (protocol == null || protocol.isBlank()) {
            protocol = OpenAiCompatibleAdapter.PROTOCOL;
        }
        String apiKey = resolveApiKey(provider.getApiKeyRef());
        String effectiveModel = resolveModelCode(modelCode, provider.getModelListJson());
        if (effectiveModel == null || effectiveModel.isBlank()) {
            log.warn("指定供应商缺少可用模型: providerCode={}", providerCode);
            return Optional.empty();
        }
        LlmProviderAdapter adapter = buildAdapter(protocol, provider, apiKey);
        if (adapter == null) {
            return Optional.empty();
        }
        return Optional.of(new ProviderRuntime(provider, adapter, effectiveModel));
    }

    private String resolveModelCode(String modelCode, String modelListJson) {
        if (modelCode == null || modelCode.isBlank()) {
            return extractDefaultModel(modelListJson);
        }
        if (modelListJson == null || modelListJson.isBlank()) {
            return null;
        }
        try {
            JsonNode array = objectMapper.readTree(modelListJson);
            if (array.isArray()) {
                for (JsonNode node : array) {
                    if (modelCode.equals(node.path("code").asText(null))) {
                        return modelCode;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("modelListJson 解析失败: {}", e.getMessage());
        }
        log.warn("指定模型不在供应商模型列表中，使用默认模型: modelCode={}", modelCode);
        return extractDefaultModel(modelListJson);
    }

    /**
     * 创建指定供应商的适配器。
     */
    public LlmProviderAdapter buildAdapter(String protocol, AiProvider provider, String apiKey) {
        String p = protocol == null ? OpenAiCompatibleAdapter.PROTOCOL : protocol;
        if (OpenAiCompatibleAdapter.PROTOCOL.equalsIgnoreCase(p)) {
            return new OpenAiCompatibleAdapter(provider, apiKey, objectMapper);
        }
        log.warn("不支持的 LLM 协议: protocol={}", protocol);
        return null;
    }

    /**
     * 从 apiKeyRef 解析 API 密钥。
     * 支持两种形式：
     * 1. 直接明文 key（本地开发/演示场景，v1.0 使用）
     * 2. JSON: {"apiKey":"sk-xxx"}（未来对接密钥保管箱的兼容格式）
     */
    public String resolveApiKey(String apiKeyRef) {
        if (apiKeyRef == null || apiKeyRef.isBlank()) {
            return "";
        }
        String trimmed = apiKeyRef.trim();
        if (trimmed.startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(trimmed);
                JsonNode keyNode = node.get("apiKey");
                if (keyNode == null) {
                    keyNode = node.get("api_key");
                }
                if (keyNode == null) {
                    keyNode = node.get("token");
                }
                return keyNode == null ? "" : keyNode.asText("");
            } catch (Exception e) {
                log.warn("apiKeyRef JSON 解析失败，按明文处理: {}", e.getMessage());
                return trimmed;
            }
        }
        return trimmed;
    }

    /**
     * 从 modelListJson 中提取第一个模型编码作为默认模型。
     */
    public String extractDefaultModel(String modelListJson) {
        if (modelListJson == null || modelListJson.isBlank()) {
            return null;
        }
        try {
            JsonNode array = objectMapper.readTree(modelListJson);
            if (array.isArray() && array.size() > 0) {
                JsonNode first = array.get(0);
                JsonNode code = first.get("code");
                return code == null ? null : code.asText();
            }
        } catch (Exception e) {
            log.warn("modelListJson 解析失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 获取当前租户所有可展示给前端的模型选项（用于场景化选择）。
     */
    public List<ModelOption> listAvailableModels() {
        String tenantId = CurrentUserContext.getTenantId();
        List<AiProvider> providers = providerMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getTenantId, tenantId)
                        .eq(AiProvider::getEnabled, true)
                        .orderByAsc(AiProvider::getPriority));

        List<ModelOption> options = new ArrayList<>();
        for (AiProvider provider : providers) {
            if (!Boolean.TRUE.equals(provider.getEnabled())) {
                continue;
            }
            parseModelOptions(provider, options);
        }
        return options;
    }

    private void parseModelOptions(AiProvider provider, List<ModelOption> options) {
        if (provider.getModelListJson() == null || provider.getModelListJson().isBlank()) {
            return;
        }
        try {
            JsonNode array = objectMapper.readTree(provider.getModelListJson());
            if (!array.isArray()) {
                return;
            }
            for (JsonNode node : array) {
                String code = node.path("code").asText(null);
                String name = node.path("name").asText(code);
                if (code != null && !code.isBlank()) {
                    options.add(new ModelOption(provider.getProviderCode(), code, name));
                }
            }
        } catch (Exception e) {
            log.warn("解析供应商模型列表失败: providerCode={}", provider.getProviderCode(), e);
        }
    }

    /**
     * 运行时供应商封装。
     */
    public record ProviderRuntime(AiProvider provider, LlmProviderAdapter adapter, String defaultModel) {
    }

    /**
     * 前端模型选项。
     */
    public record ModelOption(String providerCode, String modelCode, String modelName) {
    }
}
