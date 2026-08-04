package com.yutong.ai.gateway.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 供应商健康检查聚合响应。
 * 设计来源: P6-02 免费 LLM 供应商集成 - 健康检查能力
 * <p>
 * 包装一批供应商的健康检查结果，附带总数、可达数与检查时间戳，
 * 便于前端展示汇总信息。
 */
public record ProviderHealthResponse(
        List<ProviderHealthResult> results,
        int totalCount,
        int reachableCount,
        LocalDateTime checkedAt
) {
    public static ProviderHealthResponse of(List<ProviderHealthResult> results) {
        int reachable = 0;
        for (ProviderHealthResult r : results) {
            if (r.reachable()) {
                reachable++;
            }
        }
        return new ProviderHealthResponse(results, results.size(), reachable, LocalDateTime.now());
    }
}
