package com.yutong.ai.harness.service;

import org.springframework.stereotype.Component;

/**
 * Phase 1 默认 dry-run 执行器：回显工具名与参数，不触碰真实资源。
 * 真实 MCP/沙箱执行可在 Phase 2 以更高优先级 @Primary Bean 覆盖。
 */
@Component
public class DryRunHarnessToolExecutor implements HarnessToolExecutor {

    @Override
    public ToolExecutionResult execute(String toolName, String argumentsJson) {
        String out = "[dry-run] tool=" + toolName + " args=" + (argumentsJson == null ? "{}" : argumentsJson);
        return ToolExecutionResult.ok(out);
    }
}
