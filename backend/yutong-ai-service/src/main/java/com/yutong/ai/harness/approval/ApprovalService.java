package com.yutong.ai.harness.approval;

import com.yutong.ai.harness.domain.HarnessApproval;
import com.yutong.ai.harness.domain.HarnessRun;
import com.yutong.ai.harness.enums.ApprovalState;
import com.yutong.ai.harness.store.HarnessStore;
import com.yutong.ai.harness.store.HarnessStoreService;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 工具审批服务：创建 / resolve（常时校验 + decisionId 幂等）/ claim（一次性 CONSUMED）。
 */
@Service
public class ApprovalService {

    public static final String DECISION_APPROVE = "APPROVE";
    public static final String DECISION_DENY = "DENY";

    private final HarnessStore store;

    public ApprovalService(HarnessStore store) {
        this.store = store;
    }

    /** 创建 PENDING 审批，argumentsSha256 = hex(sha256(arguments.utf8))。 */
    @Transactional
    public HarnessApproval createApproval(String sessionId, String runId, String toolName,
                                          String toolCallId, String argumentsJson,
                                          long permissionRevision, long expectedRevision) {
        String tenantId = requireTenant();
        HarnessApproval approval = new HarnessApproval();
        approval.setId(IdGenerator.nextId());
        approval.setTenantId(tenantId);
        approval.setCreatedBy(CurrentUserContext.getUserId());
        approval.setSessionId(sessionId);
        approval.setRunId(runId);
        approval.setToolName(toolName);
        approval.setToolCallId(toolCallId);
        approval.setArgumentsJson(argumentsJson == null ? "{}" : argumentsJson);
        approval.setArgumentsSha256(CanonicalHashes.sha256Hex(approval.getArgumentsJson()));
        approval.setState(ApprovalState.PENDING.name());
        approval.setExpectedRevision(expectedRevision);
        approval.setPermissionRevision(permissionRevision);
        // 默认 24h 过期，避免无限期 PENDING
        approval.setExpiresAt(OffsetDateTime.now().plusHours(24));
        approval.setVersion(0);
        return store.insertApproval(approval);
    }

    /**
     * 解析审批：校验 expectedRevision + 常时比较 argumentsSha256 + decisionId 幂等。
     */
    @Transactional
    public HarnessApproval resolve(String approvalId, String decision, long expectedRevision,
                                   String argumentsSha256, String decisionId) {
        return resolve(approvalId, decision, expectedRevision, argumentsSha256, decisionId, null);
    }

    /**
     * 解析审批：校验 expectedRevision + 常时比较 argumentsSha256 + decisionId 幂等 + note 落库。
     *
     * @param approvalId        审批 ID
     * @param decision          APPROVE / DENY
     * @param expectedRevision  客户端持有的审批 revision
     * @param argumentsSha256   客户端重算的参数哈希
     * @param decisionId        幂等键；同 run 内已存在则直接返回既有审批
     * @param note              可选审批备注
     */
    @Transactional
    public HarnessApproval resolve(String approvalId, String decision, long expectedRevision,
                                   String argumentsSha256, String decisionId, String note) {
        String tenantId = requireTenant();
        if (!DECISION_APPROVE.equals(decision) && !DECISION_DENY.equals(decision)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "decision 仅允许 APPROVE/DENY");
        }
        HarnessApproval approval = HarnessStoreService.requirePresent(
                store.findApproval(tenantId, approvalId), "审批", approvalId);

        // decisionId 幂等
        if (decisionId != null && !decisionId.isBlank()
                && decisionId.equals(approval.getDecisionId())
                && !approval.stateEnum().canResolve()) {
            return approval;
        }
        if (decisionId != null && !decisionId.isBlank()) {
            var dup = store.findApprovalByDecisionId(approval.getRunId(), decisionId);
            if (dup.isPresent() && !dup.get().getId().equals(approvalId)) {
                throw new BusinessException(ErrorCode.SYS_IDEMPOTENCY_CONFLICT,
                        "decisionId 已被其他审批占用: " + decisionId);
            }
        }

        // 过期 fail-closed：PENDING/APPROVED 超时迁 EXPIRED
        expireIfDue(approval);

        if (!approval.stateEnum().canResolve()) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "审批已终态，不可重复 resolve: " + approval.getState());
        }
        // 常时比较参数哈希（fail-closed：不一致拒绝）
        if (!CanonicalHashes.constantTimeEquals(approval.getArgumentsSha256(), argumentsSha256)) {
            throw new BusinessException(ErrorCode.AI_TOOL_DENIED, "argumentsSha256 校验失败");
        }
        long rev = approval.getVersion() == null ? 0L : approval.getVersion();
        if (rev != expectedRevision) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK,
                    "审批 revision 冲突: expected=" + expectedRevision + " actual=" + rev);
        }

        approval.setState(DECISION_APPROVE.equals(decision)
                ? ApprovalState.APPROVED.name() : ApprovalState.DENIED.name());
        approval.setDecision(decision);
        approval.setDecisionId(decisionId == null || decisionId.isBlank() ? IdGenerator.nextId() : decisionId);
        approval.setDecidedBy(CurrentUserContext.getUserId());
        approval.setDecidedAt(OffsetDateTime.now());
        if (note != null && !note.isBlank()) {
            approval.setNote(note.length() > 500 ? note.substring(0, 500) : note);
        }
        HarnessStoreService.requireUpdated(
                store.updateApproval(approval, rev), "审批", approvalId);
        return approval;
    }

    /**
     * 一次性 claim：APPROVED → CONSUMED。
     * 校验 run 归属 + permissionRevision 与 run 快照一致。
     */
    @Transactional
    public HarnessApproval claim(String runId, String approvalId) {
        String tenantId = requireTenant();
        HarnessApproval approval = HarnessStoreService.requirePresent(
                store.findApproval(tenantId, approvalId), "审批", approvalId);
        if (!approval.getRunId().equals(runId)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "审批不属于该 Run: approval.runId=" + approval.getRunId());
        }
        // sessionId 一致性：防止事件写入错误会话
        // (controller 传入的 sessionId 与 approval 必须一致)
        // 注意：claim(runId, approvalId) 不含 sessionId 参数时由调用方保证；此处仍校验 run 归属。
        if (!approval.stateEnum().canClaim()) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "仅 APPROVED 可 claim，当前=" + approval.getState());
        }
        expireIfDue(approval);
        if (!approval.stateEnum().canClaim()) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "审批已过期，不可 claim: " + approval.getState());
        }
        HarnessRun run = HarnessStoreService.requirePresent(
                store.findRun(tenantId, runId), "Run", runId);
        long runPermRev = run.getPermissionRevision() == null ? 0L : run.getPermissionRevision();
        long approvalPermRev = approval.getPermissionRevision() == null ? 0L : approval.getPermissionRevision();
        if (runPermRev != approvalPermRev) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "权限版本已变更，审批失效: run.permissionRevision=" + runPermRev
                            + " approval.permissionRevision=" + approvalPermRev);
        }
        long rev = approval.getVersion() == null ? 0L : approval.getVersion();
        approval.setState(ApprovalState.CONSUMED.name());
        HarnessStoreService.requireUpdated(
                store.updateApproval(approval, rev), "审批", approvalId);
        return approval;
    }

    private static String requireTenant() {
        String tenantId = CurrentUserContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_UNAUTHORIZED, "缺失租户上下文");
        }
        return tenantId;
    }

    /** expires_at 已到则迁 EXPIRED（内存对象 + DB）。 */
    private void expireIfDue(HarnessApproval approval) {
        if (approval.getExpiresAt() == null) {
            return;
        }
        ApprovalState st = approval.stateEnum();
        if (st != ApprovalState.PENDING && st != ApprovalState.APPROVED) {
            return;
        }
        if (OffsetDateTime.now().isBefore(approval.getExpiresAt())) {
            return;
        }
        long rev = approval.getVersion() == null ? 0L : approval.getVersion();
        approval.setState(ApprovalState.EXPIRED.name());
        store.updateApproval(approval, rev);
    }
}
