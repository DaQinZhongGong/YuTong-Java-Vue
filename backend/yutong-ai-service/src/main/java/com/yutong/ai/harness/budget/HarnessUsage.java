package com.yutong.ai.harness.budget;

/**
 * Run 累计用量。
 */
public record HarnessUsage(
        int toolCalls,
        long inputTokens,
        long outputTokens,
        int iterations,
        long elapsedMs
) {
    public static HarnessUsage empty() {
        return new HarnessUsage(0, 0L, 0L, 0, 0L);
    }

    public HarnessUsage withToolCall() {
        return new HarnessUsage(toolCalls + 1, inputTokens, outputTokens, iterations, elapsedMs);
    }

    public HarnessUsage withIteration() {
        return new HarnessUsage(toolCalls, inputTokens, outputTokens, iterations + 1, elapsedMs);
    }

    public HarnessUsage withTokens(long addInput, long addOutput) {
        return new HarnessUsage(toolCalls, inputTokens + Math.max(0, addInput),
                outputTokens + Math.max(0, addOutput), iterations, elapsedMs);
    }
}
