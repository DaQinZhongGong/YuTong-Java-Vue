package com.yutong.ai.gateway.dto;

import java.time.LocalDateTime;

/**
 * 单个供应商健康检查结果。
 * 设计来源: P6-02 免费 LLM 供应商集成 - 健康检查能力
 */
public record ProviderHealthResult(
        String providerCode,
        String providerName,
        boolean reachable,
        long latencyMs,
        String defaultModel,
        boolean defaultModelOk,
        String errorMessage,
        LocalDateTime checkedAt
) {
    public static ProviderHealthResult ok(String providerCode, String providerName, long latencyMs,
                                          String defaultModel, boolean defaultModelOk) {
        return new ProviderHealthResult(providerCode, providerName, true, latencyMs,
                defaultModel, defaultModelOk, null, LocalDateTime.now());
    }

    public static ProviderHealthResult fail(String providerCode, String providerName, String errorMessage) {
        return new ProviderHealthResult(providerCode, providerName, false, 0,
                null, false, errorMessage, LocalDateTime.now());
    }
}
