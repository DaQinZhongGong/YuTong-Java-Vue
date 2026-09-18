package com.yutong.ai.harness.policy;

/**
 * 工具策略判定结果。优先级 DENY &gt; ASK &gt; ALLOW。
 */
public enum ToolDecision {
    ALLOW,
    DENY,
    ASK
}
