package com.yutong.ai.harness.enums;

/**
 * Harness 权限天花板。fail-closed：未知/空按最严处理。
 * 设计来源: docs/compose/spec/ai-depth-parity.md S2.1
 */
public enum PermissionMode {
    /** 只读：禁止 WRITE/EXECUTE/NETWORK/DESTRUCTIVE */
    READ_ONLY,
    /** 工作区可写：WRITE 放行，高危需审批 */
    WORKSPACE_WRITE,
    /** 完全访问：高危仍需审批（与 WORKSPACE_WRITE 同） */
    FULL_ACCESS;

    public static PermissionMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return READ_ONLY;
        }
        try {
            return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return READ_ONLY;
        }
    }

    /** 权限等级：数值越大权限越高。 */
    public int rank() {
        return switch (this) {
            case READ_ONLY -> 0;
            case WORKSPACE_WRITE -> 1;
            case FULL_ACCESS -> 2;
        };
    }
}
