package com.yutong.ai.harness.budget;

import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BudgetPolicy 超限判定单测。
 */
@DisplayName("BudgetPolicy")
class BudgetPolicyTest {

    @Test
    @DisplayName("未超限：空预算或用量在额度内")
    void withinBudget() {
        HarnessUsage usage = new HarnessUsage(2, 100, 50, 1, 1000);
        assertFalse(BudgetPolicy.isExceeded(usage, HarnessBudget.unlimited()));
        assertTrue(BudgetPolicy.breaches(usage, HarnessBudget.unlimited()).isEmpty());

        HarnessBudget budget = new HarnessBudget(5, 1000L, 1000L, 10, 60_000L);
        assertFalse(BudgetPolicy.isExceeded(usage, budget));
    }

    @Test
    @DisplayName("toolCalls 超限")
    void toolCallsExceeded() {
        HarnessUsage usage = new HarnessUsage(6, 0, 0, 0, 0);
        HarnessBudget budget = HarnessBudget.ofToolCalls(5);
        assertTrue(BudgetPolicy.isExceeded(usage, budget));
        List<String> breaches = BudgetPolicy.breaches(usage, budget);
        assertEquals(1, breaches.size());
        assertTrue(breaches.get(0).contains("toolCalls"));
    }

    @Test
    @DisplayName("token / iteration / wallTime 超限")
    void tokensAndIterations() {
        HarnessUsage usage = new HarnessUsage(0, 200, 300, 4, 5000);
        HarnessBudget budget = new HarnessBudget(null, 100L, 100L, 2, 1000L);
        List<String> breaches = BudgetPolicy.breaches(usage, budget);
        assertEquals(4, breaches.size());
    }

    @Test
    @DisplayName("enforce 超限抛 AI-429001")
    void enforceThrows() {
        HarnessUsage usage = new HarnessUsage(10, 0, 0, 0, 0);
        HarnessBudget budget = HarnessBudget.ofToolCalls(1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> BudgetPolicy.enforce(usage, budget));
        assertEquals("AI-429001", ex.errorCode().code());
    }

    @Test
    @DisplayName("enforce 未超限不抛")
    void enforcePass() {
        HarnessUsage usage = new HarnessUsage(1, 0, 0, 0, 0);
        HarnessBudget budget = HarnessBudget.ofToolCalls(5);
        BudgetPolicy.enforce(usage, budget);
    }

    @Test
    @DisplayName("firstBreach 返回首条原因")
    void firstBreach() {
        HarnessUsage usage = new HarnessUsage(9, 0, 0, 0, 0);
        HarnessBudget budget = new HarnessBudget(1, null, null, null, null);
        assertTrue(BudgetPolicy.firstBreach(usage, budget).isPresent());
        assertTrue(BudgetPolicy.firstBreach(new HarnessUsage(0, 0, 0, 0, 0), budget).isEmpty());
    }

    @Test
    @DisplayName("null 安全：usage/budget 为 null 时不超限")
    void nullSafe() {
        assertTrue(BudgetPolicy.breaches(null, HarnessBudget.unlimited()).isEmpty());
        assertTrue(BudgetPolicy.breaches(HarnessUsage.empty(), null).isEmpty());
        BudgetPolicy.enforce(null, null);
    }

    @Test
    @DisplayName("HarnessUsage 累加")
    void usageAccumulate() {
        HarnessUsage u = HarnessUsage.empty()
                .withToolCall()
                .withIteration()
                .withTokens(10, 20);
        assertEquals(1, u.toolCalls());
        assertEquals(1, u.iterations());
        assertEquals(10, u.inputTokens());
        assertEquals(20, u.outputTokens());
    }
}
