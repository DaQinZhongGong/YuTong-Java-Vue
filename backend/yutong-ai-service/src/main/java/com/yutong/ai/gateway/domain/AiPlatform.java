package com.yutong.ai.gateway.domain;

/**
 * AI 应用平台枚举 - Dify / Coze / FastGPT / 无。
 * 设计来源: 平台对接能力设计、V036 platform 列 (允许 NULL)
 * 语义: platform 为空表示直连模型供应商 (openai/deepseek 等)，非平台托管。
 */
public enum AiPlatform {

    DIFY("dify", "Dify"),
    COZE("coze", "Coze"),
    FASTGPT("fastgpt", "FastGPT"),
    NONE(null, "直连");

    private final String code;
    private final String label;

    AiPlatform(String code, String label) {
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
     * 解析 platform code，NULL/blank 返回 NONE。
     */
    public static AiPlatform of(String code) {
        if (code == null || code.isBlank()) {
            return NONE;
        }
        String normalized = code.trim().toLowerCase();
        for (AiPlatform v : values()) {
            if (normalized.equals(v.code)) {
                return v;
            }
        }
        return NONE;
    }

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return true; // NULL 视为合法（直连）
        }
        String normalized = code.trim().toLowerCase();
        for (AiPlatform v : values()) {
            if (v == NONE) continue;
            if (v.code.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
