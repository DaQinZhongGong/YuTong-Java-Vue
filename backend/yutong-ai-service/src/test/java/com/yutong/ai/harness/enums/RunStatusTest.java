package com.yutong.ai.harness.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Run 状态机单测。设计来源: ai-depth-parity.md S2.1
 */
@DisplayName("RunStatus")
class RunStatusTest {

    @Test
    @DisplayName("QUEUED → RUNNING | CANCELLED | FAILED")
    void fromQueued() {
        assertTrue(RunStatus.canTransitionTo(RunStatus.QUEUED, RunStatus.RUNNING));
        assertTrue(RunStatus.canTransitionTo(RunStatus.QUEUED, RunStatus.CANCELLED));
        assertTrue(RunStatus.canTransitionTo(RunStatus.QUEUED, RunStatus.FAILED));
        assertFalse(RunStatus.canTransitionTo(RunStatus.QUEUED, RunStatus.COMPLETED));
        assertFalse(RunStatus.canTransitionTo(RunStatus.QUEUED, RunStatus.WAITING_FOR_INPUT));
        assertFalse(RunStatus.canTransitionTo(RunStatus.QUEUED, RunStatus.WAITING_FOR_APPROVAL));
    }

    @Test
    @DisplayName("RUNNING → WAITING_* | COMPLETED | FAILED | CANCELLED")
    void fromRunning() {
        assertTrue(RunStatus.canTransitionTo(RunStatus.RUNNING, RunStatus.WAITING_FOR_APPROVAL));
        assertTrue(RunStatus.canTransitionTo(RunStatus.RUNNING, RunStatus.WAITING_FOR_INPUT));
        assertTrue(RunStatus.canTransitionTo(RunStatus.RUNNING, RunStatus.COMPLETED));
        assertTrue(RunStatus.canTransitionTo(RunStatus.RUNNING, RunStatus.FAILED));
        assertTrue(RunStatus.canTransitionTo(RunStatus.RUNNING, RunStatus.CANCELLED));
        assertFalse(RunStatus.canTransitionTo(RunStatus.RUNNING, RunStatus.QUEUED));
    }

    @Test
    @DisplayName("WAITING_* → QUEUED | CANCELLED | FAILED")
    void fromWaiting() {
        for (RunStatus from : new RunStatus[]{RunStatus.WAITING_FOR_APPROVAL, RunStatus.WAITING_FOR_INPUT}) {
            assertTrue(RunStatus.canTransitionTo(from, RunStatus.QUEUED), from + "→QUEUED");
            assertTrue(RunStatus.canTransitionTo(from, RunStatus.CANCELLED), from + "→CANCELLED");
            assertTrue(RunStatus.canTransitionTo(from, RunStatus.FAILED), from + "→FAILED");
            assertFalse(RunStatus.canTransitionTo(from, RunStatus.RUNNING), from + "→RUNNING");
            assertFalse(RunStatus.canTransitionTo(from, RunStatus.COMPLETED), from + "→COMPLETED");
        }
    }

    @Test
    @DisplayName("终态不可再迁移")
    void terminalBlocked() {
        for (RunStatus terminal : new RunStatus[]{
                RunStatus.COMPLETED, RunStatus.FAILED, RunStatus.CANCELLED}) {
            assertTrue(terminal.isTerminal());
            for (RunStatus to : RunStatus.values()) {
                assertFalse(RunStatus.canTransitionTo(terminal, to),
                        terminal + " 不应可迁移到 " + to);
            }
        }
    }

    @Test
    @DisplayName("null fail-closed")
    void nullFailClosed() {
        assertFalse(RunStatus.canTransitionTo(null, RunStatus.RUNNING));
        assertFalse(RunStatus.canTransitionTo(RunStatus.QUEUED, null));
        assertFalse(RunStatus.canTransitionTo(null, null));
    }

    @Test
    @DisplayName("isWaiting")
    void isWaiting() {
        assertTrue(RunStatus.WAITING_FOR_INPUT.isWaiting());
        assertTrue(RunStatus.WAITING_FOR_APPROVAL.isWaiting());
        assertFalse(RunStatus.RUNNING.isWaiting());
    }
}
