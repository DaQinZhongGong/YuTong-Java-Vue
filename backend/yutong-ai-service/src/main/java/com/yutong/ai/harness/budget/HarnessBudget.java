package com.yutong.ai.harness.budget;

/**
 * Run 预算。maxIterations / maxWallTimeMs 为预留字段（可为 null 表示不限制）。
 */
public record HarnessBudget(
        Integer maxToolCalls,
        Long maxInputTokens,
        Long maxOutputTokens,
        Integer maxIterations,
        Long maxWallTimeMs
) {
    public static HarnessBudget unlimited() {
        return new HarnessBudget(null, null, null, null, null);
    }

    public static HarnessBudget ofToolCalls(int maxToolCalls) {
        return new HarnessBudget(maxToolCalls, null, null, null, null);
    }
}
