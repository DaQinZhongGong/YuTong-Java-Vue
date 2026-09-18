package com.yutong.ai.harness.enums;

/**
 * 工具审批状态机：
 * PENDING → APPROVED | DENIED | EXPIRED
 * APPROVED → CONSUMED（一次性 claim）
 */
public enum ApprovalState {
    PENDING,
    APPROVED,
    DENIED,
    CONSUMED,
    EXPIRED;

    public boolean isTerminal() {
        return this == DENIED || this == CONSUMED || this == EXPIRED;
    }

    public boolean canClaim() {
        return this == APPROVED;
    }

    public boolean canResolve() {
        return this == PENDING;
    }
}
