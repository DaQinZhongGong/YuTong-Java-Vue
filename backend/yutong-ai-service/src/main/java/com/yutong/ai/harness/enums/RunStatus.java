package com.yutong.ai.harness.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Run 状态机。设计来源: ai-depth-parity.md S2.1
 *
 * <pre>
 * QUEUED → RUNNING | CANCELLED | FAILED
 * RUNNING → WAITING_FOR_APPROVAL | WAITING_FOR_INPUT | COMPLETED | FAILED | CANCELLED
 * WAITING_* → QUEUED | CANCELLED | FAILED
 * terminal: COMPLETED / FAILED / CANCELLED
 * </pre>
 */
public enum RunStatus {
    QUEUED,
    RUNNING,
    WAITING_FOR_APPROVAL,
    WAITING_FOR_INPUT,
    COMPLETED,
    FAILED,
    CANCELLED;

    private static final Set<RunStatus> TERMINAL = EnumSet.of(COMPLETED, FAILED, CANCELLED);

    private static final Set<RunStatus> FROM_QUEUED =
            EnumSet.of(RUNNING, CANCELLED, FAILED);
    private static final Set<RunStatus> FROM_RUNNING =
            EnumSet.of(WAITING_FOR_APPROVAL, WAITING_FOR_INPUT, COMPLETED, FAILED, CANCELLED);
    private static final Set<RunStatus> FROM_WAITING =
            EnumSet.of(QUEUED, CANCELLED, FAILED);

    public boolean isTerminal() {
        return TERMINAL.contains(this);
    }

    public boolean isWaiting() {
        return this == WAITING_FOR_APPROVAL || this == WAITING_FOR_INPUT;
    }

    /**
     * 状态迁移判定。fail-closed：未知迁移返回 false，null 目标返回 false。
     */
    public static boolean canTransitionTo(RunStatus from, RunStatus to) {
        if (from == null || to == null) {
            return false;
        }
        if (from.isTerminal()) {
            return false;
        }
        return switch (from) {
            case QUEUED -> FROM_QUEUED.contains(to);
            case RUNNING -> FROM_RUNNING.contains(to);
            case WAITING_FOR_APPROVAL, WAITING_FOR_INPUT -> FROM_WAITING.contains(to);
            default -> false;
        };
    }
}
