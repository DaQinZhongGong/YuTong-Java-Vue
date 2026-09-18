package com.yutong.ai.harness.policy;

import com.yutong.ai.harness.enums.ApprovalPolicy;
import com.yutong.ai.harness.enums.PermissionMode;
import com.yutong.ai.harness.enums.ToolCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ToolPolicyEngine 纯函数单测。fail-closed。
 */
@DisplayName("ToolPolicyEngine")
class ToolPolicyEngineTest {

    @Test
    @DisplayName("READ_ONLY 允许 READ/SEARCH")
    void readOnlyAllowsReadSearch() {
        assertEquals(ToolDecision.ALLOW, ToolPolicyEngine.evaluate(
                PermissionMode.READ_ONLY, ApprovalPolicy.ON_REQUEST, Set.of(ToolCapability.READ)));
        assertEquals(ToolDecision.ALLOW, ToolPolicyEngine.evaluate(
                PermissionMode.READ_ONLY, ApprovalPolicy.ON_REQUEST, Set.of(ToolCapability.SEARCH)));
    }

    @Test
    @DisplayName("READ_ONLY 拒绝 WRITE/EXECUTE/NETWORK/DESTRUCTIVE")
    void readOnlyDeniesDangerous() {
        for (ToolCapability cap : new ToolCapability[]{
                ToolCapability.WRITE, ToolCapability.EXECUTE,
                ToolCapability.NETWORK, ToolCapability.DESTRUCTIVE}) {
            assertEquals(ToolDecision.DENY, ToolPolicyEngine.evaluate(
                    PermissionMode.READ_ONLY, ApprovalPolicy.ON_REQUEST, Set.of(cap)),
                    "READ_ONLY 应拒绝 " + cap);
            assertEquals(ToolDecision.DENY, ToolPolicyEngine.evaluate(
                    PermissionMode.READ_ONLY, ApprovalPolicy.NEVER, Set.of(cap)),
                    "NEVER 不突破 READ_ONLY 天花板");
        }
    }

    @Test
    @DisplayName("WORKSPACE_WRITE 放行 WRITE")
    void workspaceWriteAllowsWrite() {
        assertEquals(ToolDecision.ALLOW, ToolPolicyEngine.evaluate(
                PermissionMode.WORKSPACE_WRITE, ApprovalPolicy.ON_REQUEST, Set.of(ToolCapability.WRITE)));
    }

    @Test
    @DisplayName("WORKSPACE_WRITE 高危 ASK；NEVER 时 ALLOW")
    void workspaceWriteHighRiskAsk() {
        assertEquals(ToolDecision.ASK, ToolPolicyEngine.evaluate(
                PermissionMode.WORKSPACE_WRITE, ApprovalPolicy.ON_REQUEST, Set.of(ToolCapability.EXECUTE)));
        assertEquals(ToolDecision.ASK, ToolPolicyEngine.evaluate(
                PermissionMode.WORKSPACE_WRITE, ApprovalPolicy.ON_REQUEST, Set.of(ToolCapability.NETWORK)));
        assertEquals(ToolDecision.ASK, ToolPolicyEngine.evaluate(
                PermissionMode.WORKSPACE_WRITE, ApprovalPolicy.ON_REQUEST, Set.of(ToolCapability.DESTRUCTIVE)));
        assertEquals(ToolDecision.ALLOW, ToolPolicyEngine.evaluate(
                PermissionMode.WORKSPACE_WRITE, ApprovalPolicy.NEVER, Set.of(ToolCapability.EXECUTE)));
    }

    @Test
    @DisplayName("FULL_ACCESS 高危同 WORKSPACE_WRITE；普通能力 ALLOW")
    void fullAccess() {
        assertEquals(ToolDecision.ALLOW, ToolPolicyEngine.evaluate(
                PermissionMode.FULL_ACCESS, ApprovalPolicy.ON_REQUEST, Set.of(ToolCapability.WRITE)));
        assertEquals(ToolDecision.ASK, ToolPolicyEngine.evaluate(
                PermissionMode.FULL_ACCESS, ApprovalPolicy.ON_REQUEST, Set.of(ToolCapability.DESTRUCTIVE)));
        assertEquals(ToolDecision.ALLOW, ToolPolicyEngine.evaluate(
                PermissionMode.FULL_ACCESS, ApprovalPolicy.NEVER, Set.of(ToolCapability.NETWORK)));
    }

    @Test
    @DisplayName("混合能力按最严：DENY > ASK > ALLOW")
    void mixedCapabilitiesPrecedence() {
        // READ_ONLY + WRITE+READ → DENY
        assertEquals(ToolDecision.DENY, ToolPolicyEngine.evaluate(
                PermissionMode.READ_ONLY, ApprovalPolicy.ON_REQUEST,
                Set.of(ToolCapability.READ, ToolCapability.WRITE)));
        // WORKSPACE_WRITE + WRITE+EXECUTE → ASK（高危覆盖 ALLOW）
        assertEquals(ToolDecision.ASK, ToolPolicyEngine.evaluate(
                PermissionMode.WORKSPACE_WRITE, ApprovalPolicy.ON_REQUEST,
                Set.of(ToolCapability.WRITE, ToolCapability.EXECUTE)));
    }

    @Test
    @DisplayName("fail-closed：null mode / 空能力 / null policy")
    void failClosed() {
        assertEquals(ToolDecision.DENY, ToolPolicyEngine.evaluate(
                null, ApprovalPolicy.ON_REQUEST, Set.of(ToolCapability.READ)));
        assertEquals(ToolDecision.DENY, ToolPolicyEngine.evaluate(
                PermissionMode.FULL_ACCESS, ApprovalPolicy.ON_REQUEST, Set.of()));
        assertEquals(ToolDecision.DENY, ToolPolicyEngine.evaluate(
                PermissionMode.FULL_ACCESS, ApprovalPolicy.ON_REQUEST, null));
        // null policy 按 ON_REQUEST（更严）
        assertEquals(ToolDecision.ASK, ToolPolicyEngine.evaluate(
                PermissionMode.FULL_ACCESS, null, Set.of(ToolCapability.EXECUTE)));
    }
}
