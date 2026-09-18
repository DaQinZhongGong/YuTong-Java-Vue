package com.yutong.ai.harness.enums;

/**
 * 工具能力标签。策略引擎按能力集合判定 ALLOW/DENY/ASK。
 */
public enum ToolCapability {
    READ,
    SEARCH,
    WRITE,
    EXECUTE,
    NETWORK,
    DESTRUCTIVE
}
