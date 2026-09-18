package com.yutong.ai.harness.policy;

import com.yutong.ai.harness.enums.ApprovalPolicy;
import com.yutong.ai.harness.enums.PermissionMode;
import com.yutong.ai.harness.enums.ToolCapability;

import java.util.Set;

/**
 * 工具策略引擎（fail-closed）。
 *
 * <p>规则（ai-depth-parity.md S2.1）:
 * <ul>
 *   <li>READ_ONLY：含 WRITE/EXECUTE/NETWORK/DESTRUCTIVE → DENY</li>
 *   <li>WORKSPACE_WRITE：WRITE → ALLOW；EXECUTE/NETWORK/DESTRUCTIVE → ASK（NEVER 时 ALLOW）</li>
 *   <li>FULL_ACCESS：高危同 WORKSPACE_WRITE；其余 ALLOW</li>
 * </ul>
 *
 * <p>fail-closed：mode/policy/capabilities 为空或未知能力标签 → DENY。
 */
public final class ToolPolicyEngine {

    private ToolPolicyEngine() {
    }

    private static final Set<ToolCapability> HIGH_RISK =
            Set.of(ToolCapability.EXECUTE, ToolCapability.NETWORK, ToolCapability.DESTRUCTIVE);
    private static final Set<ToolCapability> READ_ONLY_BLOCKED =
            Set.of(ToolCapability.WRITE, ToolCapability.EXECUTE, ToolCapability.NETWORK, ToolCapability.DESTRUCTIVE);

    public static ToolDecision evaluate(PermissionMode mode, ApprovalPolicy policy, Set<ToolCapability> capabilities) {
        if (mode == null) {
            return ToolDecision.DENY;
        }
        if (capabilities == null || capabilities.isEmpty()) {
            // 未知能力：fail-closed
            return ToolDecision.DENY;
        }
        // null policy 按更严的 ON_REQUEST
        ApprovalPolicy effectivePolicy = policy == null ? ApprovalPolicy.ON_REQUEST : policy;

        boolean hasHighRisk = capabilities.stream().anyMatch(HIGH_RISK::contains);

        return switch (mode) {
            case READ_ONLY -> {
                boolean blocked = capabilities.stream().anyMatch(READ_ONLY_BLOCKED::contains);
                yield blocked ? ToolDecision.DENY : ToolDecision.ALLOW;
            }
            case WORKSPACE_WRITE, FULL_ACCESS -> {
                if (hasHighRisk) {
                    yield effectivePolicy == ApprovalPolicy.NEVER ? ToolDecision.ALLOW : ToolDecision.ASK;
                }
                // 仅 READ/SEARCH/WRITE：两种可写模式均放行
                yield ToolDecision.ALLOW;
            }
        };
    }
}
