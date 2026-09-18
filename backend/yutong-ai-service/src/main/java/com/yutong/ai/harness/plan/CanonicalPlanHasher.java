package com.yutong.ai.harness.plan;

import com.yutong.ai.harness.approval.CanonicalHashes;

/**
 * 计划规范哈希。
 * canonicalHash = SHA-256(schemaVersion|taskId|revision|mode|reviewState|planMarkdown|steps)
 */
public final class CanonicalPlanHasher {

    public static final String SCHEMA_VERSION = "1";

    private CanonicalPlanHasher() {
    }

    public static String hash(String taskId, long revision, String mode, String reviewState,
                              String planMd, String stepsJson) {
        String canonical = String.join("|",
                SCHEMA_VERSION,
                nullToEmpty(taskId),
                Long.toString(revision),
                nullToEmpty(mode),
                nullToEmpty(reviewState),
                nullToEmpty(planMd),
                nullToEmpty(stepsJson));
        return CanonicalHashes.sha256Hex(canonical);
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
