package com.yutong.ai.harness.service;

import java.util.Map;

/**
 * Harness 工具执行器。Phase 1 默认 dry-run；真实执行可后接 MCP。
 */
public interface HarnessToolExecutor {

    ToolExecutionResult execute(String toolName, String argumentsJson);

    record ToolExecutionResult(boolean success, String output, Map<String, Object> metadata) {
        public static ToolExecutionResult ok(String output) {
            return new ToolExecutionResult(true, output, Map.of());
        }

        public static ToolExecutionResult fail(String error) {
            return new ToolExecutionResult(false, error, Map.of());
        }
    }
}
