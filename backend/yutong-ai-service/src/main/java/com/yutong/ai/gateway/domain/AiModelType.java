package com.yutong.ai.gateway.domain;

/**
 * AI 模型类型枚举 - 9 枚举。
 * 设计来源: AI 模块能力设计、V036__ai_provider_parity.sql chk_ai_provider_model_type
 * 约束: code 与 DDL CHECK 完全一致。
 */
public enum AiModelType {

    CHAT("chat", "对话"),
    IMAGE("image", "图像"),
    VECTOR("vector", "向量/Embedding"),
    RERANKER("reranker", "重排"),
    AUDIO("audio", "音频"),
    TEXT("text", "文本"),
    VIDEO("video", "视频"),
    PPT("ppt", "演示文稿"),
    MUSIC("music", "音乐");

    private final String code;
    private final String label;

    AiModelType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static AiModelType of(String code) {
        if (code == null || code.isBlank()) {
            return CHAT;
        }
        String normalized = code.trim().toLowerCase();
        for (AiModelType v : values()) {
            if (v.code.equals(normalized)) {
                return v;
            }
        }
        return CHAT;
    }

    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = code.trim().toLowerCase();
        for (AiModelType v : values()) {
            if (v.code.equals(normalized)) {
                return true;
            }
        }
        return false;
    }
}
