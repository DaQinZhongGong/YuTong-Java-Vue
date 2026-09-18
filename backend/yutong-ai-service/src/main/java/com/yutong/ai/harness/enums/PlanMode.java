package com.yutong.ai.harness.enums;

/**
 * 计划模式（对应 V059 chk_ai_harness_plan_mode）。
 */
public enum PlanMode {
    PLAN,
    BUILD,
    VERIFY,
    BLOCKED,
    COMPLETED,
    FAILED;

    public static PlanMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return PLAN;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PLAN;
        }
    }
}
