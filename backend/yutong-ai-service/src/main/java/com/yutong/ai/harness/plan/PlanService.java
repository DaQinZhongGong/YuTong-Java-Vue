package com.yutong.ai.harness.plan;

import com.yutong.ai.harness.domain.HarnessPlan;
import com.yutong.ai.harness.enums.PlanMode;
import com.yutong.ai.harness.enums.PlanReviewState;
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
 * 计划服务：创建（canonicalHash）+ approve（expectedRevision + expectedHash 常时比较 + 幂等）。
 */
@Service
public class PlanService {

    private final HarnessStore store;

    public PlanService(HarnessStore store) {
        this.store = store;
    }

    /** 创建计划草稿并进入 AWAITING_APPROVAL。 */
    @Transactional
    public HarnessPlan createPlan(String sessionId, String runId, String taskId,
                                  String planMd, String stepsJson) {
        String tenantId = requireTenant();
        HarnessPlan plan = new HarnessPlan();
        plan.setId(IdGenerator.nextId());
        plan.setTenantId(tenantId);
        plan.setCreatedBy(CurrentUserContext.getUserId());
        plan.setSessionId(sessionId);
        plan.setRunId(runId);
        plan.setTaskId(taskId == null || taskId.isBlank() ? IdGenerator.nextId() : taskId);
        plan.setMode(PlanMode.PLAN.name());
        plan.setReviewState(PlanReviewState.AWAITING_APPROVAL.name());
        plan.setPlanMd(planMd == null ? "" : planMd);
        plan.setStepsJson(stepsJson == null ? "[]" : stepsJson);
        plan.setExpectedRevision(0L);
        long rev = 0L;
        plan.setCanonicalHash(CanonicalPlanHasher.hash(
                plan.getTaskId(), rev, plan.getMode(), plan.getReviewState(),
                plan.getPlanMd(), plan.getStepsJson()));
        plan.setVersion(0);
        return store.insertPlan(plan);
    }

    /**
     * 批准计划：expectedRevision + expectedHash 常时比较 + idempotencyKey 幂等。
     */
    @Transactional
    public HarnessPlan approve(String planId, long expectedRevision, String expectedHash,
                               String idempotencyKey) {
        String tenantId = requireTenant();
        HarnessPlan plan = HarnessStoreService.requirePresent(
                store.findPlan(tenantId, planId), "计划", planId);

        // 幂等：仅当同 planId 且已 APPROVED 才直接返回；key 挂在其他 plan 上则冲突
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = store.findPlanByIdempotencyKey(plan.getRunId(), idempotencyKey);
            if (existing.isPresent()) {
                if (existing.get().getId().equals(planId)
                        && PlanReviewState.APPROVED.name().equals(existing.get().getReviewState())) {
                    return existing.get();
                }
                throw new BusinessException(ErrorCode.SYS_IDEMPOTENCY_CONFLICT,
                        "idempotencyKey 已绑定其他计划: " + idempotencyKey);
            }
        }

        if (!PlanReviewState.AWAITING_APPROVAL.name().equals(plan.getReviewState())) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "仅 AWAITING_APPROVAL 可批准，当前=" + plan.getReviewState());
        }

        long rev = plan.getVersion() == null ? 0L : plan.getVersion();
        if (rev != expectedRevision) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK,
                    "计划 revision 冲突: expected=" + expectedRevision + " actual=" + rev);
        }
        if (!constantTimeEquals(plan.getCanonicalHash(), expectedHash)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "计划哈希校验失败");
        }

        plan.setReviewState(PlanReviewState.APPROVED.name());
        plan.setMode(PlanMode.BUILD.name());
        plan.setIdempotencyKey(idempotencyKey);
        plan.setApprovedBy(CurrentUserContext.getUserId());
        plan.setApprovedAt(OffsetDateTime.now());
        // 批准后重算 hash（reviewState/mode 变化）
        plan.setCanonicalHash(CanonicalPlanHasher.hash(
                plan.getTaskId(), rev, plan.getMode(), plan.getReviewState(),
                plan.getPlanMd(), plan.getStepsJson()));
        plan.setExpectedRevision(rev);
        HarnessStoreService.requireUpdated(store.updatePlan(plan, rev), "计划", planId);
        return plan;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return com.yutong.ai.harness.approval.CanonicalHashes.constantTimeEquals(a, b);
    }

    private static String requireTenant() {
        String tenantId = CurrentUserContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_UNAUTHORIZED, "缺失租户上下文");
        }
        return tenantId;
    }
}
