package com.yutong.ai.gateway.domain;

/**
 * AI 供应商类型枚举 — 多厂商 13 枚举。
 * 设计来源: AI 模块能力设计、YuTong 13-AI能力设计、V036__ai_provider_parity.sql
 * 约束: code 与 DB chk_ai_provider_provider_type 完全一致，新增值需同步 DDL。
 */
public enum AiProviderType {

    OPENAI("openai", "OpenAI"),
    DEEPSEEK("deepseek", "DeepSeek"),
    QIANWEN("qianwen", "通义千问"),
    ZHIPU("zhipu", "智谱 GLM"),
    OLLAMA("ollama", "Ollama 本地"),
    MINIMAX("minimax", "MiniMax"),
    ATLAS("atlas", "Atlas"),
    XIAOMI("xiaomi", "小米"),
    DIFY("dify", "Dify 平台"),
    COZE("coze", "Coze 平台"),
    PPIO("ppio", "PPIO"),
    ATLA("atla", "Atla"),
    CUSTOM_API("custom_api", "自定义 API");

    private final String code;
    private final String label;

    AiProviderType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 大小写不敏感解析，未匹配返回 CUSTOM_API（安全默认，避免 NPE）。
     */
    public static AiProviderType of(String code) {
        if (code == null || code.isBlank()) {
            return CUSTOM_API;
        }
        String normalized = code.trim().toLowerCase();
        for (AiProviderType v : values()) {
            if (v.code.equals(normalized)) {
                return v;
            }
        }
        return CUSTOM_API;
    }

    /**
     * 严格校验是否存在该 code，不存在返回 false。
     */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.trim().toLowerCase();
        for (AiProviderType v : values()) {
            if (v.code.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
