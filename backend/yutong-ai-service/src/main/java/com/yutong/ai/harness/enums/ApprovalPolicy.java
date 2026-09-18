package com.yutong.ai.harness.enums;

/**
 * 工具审批策略。NEVER 仅在权限模式允许高危时跳过 ASK，不突破 mode 天花板。
 */
public enum ApprovalPolicy {
    /** 高危工具需要人工审批 */
    ON_REQUEST,
    /** 从不审批（高危在允许模式下直接放行） */
    NEVER;

    public static ApprovalPolicy parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return ON_REQUEST;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return ON_REQUEST;
        }
    }
}
