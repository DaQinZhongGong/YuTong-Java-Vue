package com.yutong.ai.harness.enums;

/**
 * Harness 事件账本类型。落库使用 code（与 SSE event name / spec 一致）。
 */
public enum HarnessEventType {
    RUN_QUEUED("run.queued"),
    RUN_STARTED("run.started"),
    RUN_COMPLETED("run.completed"),
    RUN_FAILED("run.failed"),
    RUN_CANCELLED("run.cancelled"),
    RUN_CANCEL_REQUESTED("run.cancel_requested"),

    PLAN_CREATED("plan.created"),
    PLAN_AWAITING_APPROVAL("plan.awaiting_approval"),
    PLAN_APPROVED("plan.approved"),
    PLAN_REVISION_REQUESTED("plan.revision_requested"),

    TOOL_CALL("tool.call"),
    TOOL_RESULT("tool.result"),
    TOOL_DENIED("tool.denied"),

    APPROVAL_REQUESTED("approval.requested"),
    APPROVAL_RESOLVED("approval.resolved"),
    APPROVAL_CONSUMED("approval.consumed"),

    USER_INPUT("user.input"),
    BUDGET_EXCEEDED("budget.exceeded");

    private final String code;

    HarnessEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static HarnessEventType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (HarnessEventType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
