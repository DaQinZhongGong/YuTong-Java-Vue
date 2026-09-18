package com.yutong.ai.harness.budget;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 预算策略：超限判定。超限 → Run FAILED + 事件 budget.exceeded（由 HarnessService 落账）。
 */
public final class BudgetPolicy {

    private BudgetPolicy() {
    }

    /** 返回所有超限维度描述；空列表表示未超限。 */
    public static List<String> breaches(HarnessUsage usage, HarnessBudget budget) {
        List<String> out = new ArrayList<>();
        if (usage == null || budget == null) {
            return out;
        }
        if (budget.maxToolCalls() != null && usage.toolCalls() > budget.maxToolCalls()) {
            out.add("toolCalls " + usage.toolCalls() + " > " + budget.maxToolCalls());
        }
        if (budget.maxInputTokens() != null && usage.inputTokens() > budget.maxInputTokens()) {
            out.add("inputTokens " + usage.inputTokens() + " > " + budget.maxInputTokens());
        }
        if (budget.maxOutputTokens() != null && usage.outputTokens() > budget.maxOutputTokens()) {
            out.add("outputTokens " + usage.outputTokens() + " > " + budget.maxOutputTokens());
        }
        if (budget.maxIterations() != null && usage.iterations() > budget.maxIterations()) {
            out.add("iterations " + usage.iterations() + " > " + budget.maxIterations());
        }
        if (budget.maxWallTimeMs() != null && usage.elapsedMs() > budget.maxWallTimeMs()) {
            out.add("wallTimeMs " + usage.elapsedMs() + " > " + budget.maxWallTimeMs());
        }
        return out;
    }

    public static boolean isExceeded(HarnessUsage usage, HarnessBudget budget) {
        return !breaches(usage, budget).isEmpty();
    }

    /** 超限时抛出 AI_QUOTA_EXCEEDED；否则静默通过。 */
    public static void enforce(HarnessUsage usage, HarnessBudget budget) {
        List<String> list = breaches(usage, budget);
        if (!list.isEmpty()) {
            throw new BusinessException(ErrorCode.AI_QUOTA_EXCEEDED,
                    "预算超限: " + String.join("; ", list));
        }
    }

    /** 返回第一条超限原因，便于写入 errorMessage。 */
    public static Optional<String> firstBreach(HarnessUsage usage, HarnessBudget budget) {
        List<String> list = breaches(usage, budget);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
