package com.yutong.ai.gateway.domain;

/**
 * 多模态能力枚举，映射 /media/* 能力声明。
 * 设计来源: V036 multimodal_capabilities jsonb、Phase 2 媒体能力设计
 * 值对应 multimodal_capabilities JSON 的 key，如 {"image":true,"video":true}
 */
public enum AiMultimodalCapability {

    IMAGE("image", "图像"),
    VIDEO("video", "视频"),
    AUDIO("audio", "音频"),
    PPT("ppt", "演示文稿");

    private final String code;
    private final String label;

    AiMultimodalCapability(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static AiMultimodalCapability of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String normalized = code.trim().toLowerCase();
        for (AiMultimodalCapability v : values()) {
            if (v.code.equals(normalized)) {
                return v;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return of(code) != null;
    }
}
