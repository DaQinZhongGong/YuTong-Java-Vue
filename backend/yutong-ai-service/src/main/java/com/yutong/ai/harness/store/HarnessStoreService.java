package com.yutong.ai.harness.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yutong.ai.harness.domain.HarnessApproval;
import com.yutong.ai.harness.domain.HarnessEvent;
import com.yutong.ai.harness.domain.HarnessPlan;
import com.yutong.ai.harness.domain.HarnessRun;
import com.yutong.ai.harness.domain.HarnessSession;
import com.yutong.ai.harness.mapper.AiHarnessApprovalMapper;
import com.yutong.ai.harness.mapper.AiHarnessEventMapper;
import com.yutong.ai.harness.mapper.AiHarnessPlanMapper;
import com.yutong.ai.harness.mapper.AiHarnessRunMapper;
import com.yutong.ai.harness.mapper.AiHarnessSessionMapper;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * HarnessStore 的 MyBatis-Plus 实现。
 * 业务 revision 乐观锁：UPDATE ... SET revision = expected+1 WHERE id=? AND revision=expected。
 * Mapper 必须位于 com.yutong.**.mapper（@MapperScan 契约）。
 */
@Service
public class HarnessStoreService implements HarnessStore {

    private static final int EVENT_SEQ_MAX_RETRY = 5;

    private final AiHarnessSessionMapper sessionMapper;
    private final AiHarnessRunMapper runMapper;
    private final AiHarnessEventMapper eventMapper;
    private final AiHarnessApprovalMapper approvalMapper;
    private final AiHarnessPlanMapper planMapper;

    public HarnessStoreService(AiHarnessSessionMapper sessionMapper,
                               AiHarnessRunMapper runMapper,
                               AiHarnessEventMapper eventMapper,
                               AiHarnessApprovalMapper approvalMapper,
                               AiHarnessPlanMapper planMapper) {
        this.sessionMapper = sessionMapper;
        this.runMapper = runMapper;
        this.eventMapper = eventMapper;
        this.approvalMapper = approvalMapper;
        this.planMapper = planMapper;
    }

    // ===== Session =====

    @Override
    public HarnessSession insertSession(HarnessSession session) {
        sessionMapper.insert(session);
        return session;
    }

    @Override
    public Optional<HarnessSession> findSession(String tenantId, String id) {
        return findSession(tenantId, id, false);
    }

    @Override
    public Optional<HarnessSession> findSession(String tenantId, String id, boolean includeDeleted) {
        LambdaQueryWrapper<HarnessSession> q = new LambdaQueryWrapper<HarnessSession>()
                .eq(HarnessSession::getTenantId, tenantId)
                .eq(HarnessSession::getId, id);
        if (!includeDeleted) {
            q.eq(HarnessSession::getDeleted, false);
        }
        return Optional.ofNullable(sessionMapper.selectOne(q));
    }

    @Override
    public Optional<HarnessSession> findSessionByIdempotencyKey(String tenantId, String userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessionMapper.selectOne(new LambdaQueryWrapper<HarnessSession>()
                .eq(HarnessSession::getTenantId, tenantId)
                .eq(HarnessSession::getUserId, userId)
                .eq(HarnessSession::getIdempotencyKey, idempotencyKey)
                .eq(HarnessSession::getDeleted, false)
                .last("LIMIT 1")));
    }

    @Override
    public List<HarnessSession> listSessions(String tenantId, String userId, boolean includeDeleted) {
        LambdaQueryWrapper<HarnessSession> q = new LambdaQueryWrapper<HarnessSession>()
                .eq(HarnessSession::getTenantId, tenantId)
                .eq(HarnessSession::getUserId, userId);
        if (!includeDeleted) {
            q.eq(HarnessSession::getDeleted, false);
        }
        q.orderByDesc(HarnessSession::getPinnedAt)
                .orderByDesc(HarnessSession::getCreatedTime);
        return sessionMapper.selectList(q);
    }

    @Override
    public boolean updateSession(HarnessSession session, long expectedRevision) {
        long next = expectedRevision + 1;
        int rows = sessionMapper.update(null, new LambdaUpdateWrapper<HarnessSession>()
                .eq(HarnessSession::getId, session.getId())
                .eq(HarnessSession::getTenantId, session.getTenantId())
                .eq(HarnessSession::getRevision, expectedRevision)
                .eq(HarnessSession::getDeleted, false)
                .set(HarnessSession::getTitle, session.getTitle())
                .set(HarnessSession::getWorkspacePath, session.getWorkspacePath())
                .set(HarnessSession::getWorkspaceManifest, session.getWorkspaceManifest())
                .set(HarnessSession::getModel, session.getModel())
                .set(HarnessSession::getPermissionMode, session.getPermissionMode())
                .set(HarnessSession::getApprovalPolicy, session.getApprovalPolicy())
                .set(HarnessSession::getThinkingLevel, session.getThinkingLevel())
                .set(HarnessSession::getVerificationMode, session.getVerificationMode())
                .set(HarnessSession::getActiveRunId, session.getActiveRunId())
                .set(HarnessSession::getPinnedAt, session.getPinnedAt())
                .set(HarnessSession::getRevision, next)
                .set(HarnessSession::getUpdatedTime, OffsetDateTime.now()));
        if (rows == 1) {
            session.setRevision(next);
            return true;
        }
        return false;
    }

    @Override
    public boolean softDeleteSession(String tenantId, String id) {
        return sessionMapper.delete(new LambdaQueryWrapper<HarnessSession>()
                .eq(HarnessSession::getId, id)
                .eq(HarnessSession::getTenantId, tenantId)
                .eq(HarnessSession::getDeleted, false)) > 0;
    }

    @Override
    public boolean restoreSession(String tenantId, String id, String userId) {
        return sessionMapper.restoreById(id, tenantId, userId) > 0;
    }

    @Override
    public boolean pinSession(String tenantId, String id, OffsetDateTime pinnedAt, long expectedRevision) {
        int rows = sessionMapper.update(null, new LambdaUpdateWrapper<HarnessSession>()
                .eq(HarnessSession::getId, id)
                .eq(HarnessSession::getTenantId, tenantId)
                .eq(HarnessSession::getRevision, expectedRevision)
                .eq(HarnessSession::getDeleted, false)
                .set(HarnessSession::getPinnedAt, pinnedAt)
                .set(HarnessSession::getRevision, expectedRevision + 1)
                .set(HarnessSession::getUpdatedTime, OffsetDateTime.now()));
        return rows == 1;
    }

    // ===== Run =====

    @Override
    public HarnessRun insertRun(HarnessRun run) {
        runMapper.insert(run);
        return run;
    }

    @Override
    public Optional<HarnessRun> findRun(String tenantId, String id) {
        return Optional.ofNullable(runMapper.selectOne(new LambdaQueryWrapper<HarnessRun>()
                .eq(HarnessRun::getId, id)
                .eq(HarnessRun::getTenantId, tenantId)
                .eq(HarnessRun::getDeleted, false)));
    }

    @Override
    public Optional<HarnessRun> findRunByIdempotencyKey(String sessionId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(runMapper.selectOne(new LambdaQueryWrapper<HarnessRun>()
                .eq(HarnessRun::getSessionId, sessionId)
                .eq(HarnessRun::getIdempotencyKey, idempotencyKey)
                .eq(HarnessRun::getDeleted, false)
                .last("LIMIT 1")));
    }

    @Override
    public List<HarnessRun> listRuns(String tenantId, String sessionId) {
        return runMapper.selectList(new LambdaQueryWrapper<HarnessRun>()
                .eq(HarnessRun::getSessionId, sessionId)
                .eq(HarnessRun::getTenantId, tenantId)
                .eq(HarnessRun::getDeleted, false)
                .orderByDesc(HarnessRun::getCreatedTime));
    }

    @Override
    public boolean updateRun(HarnessRun run, long expectedRevision) {
        long next = expectedRevision + 1;
        int rows = runMapper.update(null, new LambdaUpdateWrapper<HarnessRun>()
                .eq(HarnessRun::getId, run.getId())
                .eq(HarnessRun::getTenantId, run.getTenantId())
                .eq(HarnessRun::getRevision, expectedRevision)
                .eq(HarnessRun::getDeleted, false)
                .set(HarnessRun::getStatus, run.getStatus())
                .set(HarnessRun::getPermissionMode, run.getPermissionMode())
                .set(HarnessRun::getPermissionRevision, run.getPermissionRevision())
                .set(HarnessRun::getBudgetJson, run.getBudgetJson())
                .set(HarnessRun::getUsageJson, run.getUsageJson())
                .set(HarnessRun::getPlanJson, run.getPlanJson())
                .set(HarnessRun::getIteration, run.getIteration())
                .set(HarnessRun::getToolCallCount, run.getToolCallCount())
                .set(HarnessRun::getCancelRequested, run.getCancelRequested())
                .set(HarnessRun::getErrorMessage, run.getErrorMessage())
                .set(HarnessRun::getRevision, next)
                .set(HarnessRun::getUpdatedTime, OffsetDateTime.now()));
        if (rows == 1) {
            run.setRevision(next);
            return true;
        }
        return false;
    }

    // ===== Event =====

    /**
     * 预取下一序号（非原子，仅供展示/预填）。
     * 真正落库以 {@link #insertEvent} 唯一索引重试为准，多实例安全。
     */
    @Override
    public long nextEventSequence(String runId) {
        return eventMapper.selectMaxSequence(runId) + 1;
    }

    @Override
    public HarnessEvent insertEvent(HarnessEvent event) {
        int attempt = 0;
        while (true) {
            attempt++;
            if (event.getSequenceNo() == null || event.getSequenceNo() <= 0
                    || attempt > 1) {
                event.setSequenceNo(eventMapper.selectMaxSequence(event.getRunId()) + 1);
            }
            try {
                eventMapper.insert(event);
                return event;
            } catch (DuplicateKeyException ex) {
                if (attempt >= EVENT_SEQ_MAX_RETRY) {
                    throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                            "事件序号冲突: runId=" + event.getRunId() + " seq=" + event.getSequenceNo());
                }
                // 多实例/并发：靠 (run_id, sequence_no) 唯一索引 + 重试，无 JVM 锁泄漏
            }
        }
    }

    @Override
    public List<HarnessEvent> listEvents(String runId, long afterSequence, int limit) {
        int size = limit <= 0 ? 200 : Math.min(limit, 1000);
        return eventMapper.selectList(new LambdaQueryWrapper<HarnessEvent>()
                .eq(HarnessEvent::getRunId, runId)
                .gt(HarnessEvent::getSequenceNo, afterSequence)
                .orderByAsc(HarnessEvent::getSequenceNo)
                .last("LIMIT " + size));
    }

    // ===== Approval =====

    @Override
    public HarnessApproval insertApproval(HarnessApproval approval) {
        approvalMapper.insert(approval);
        return approval;
    }

    @Override
    public Optional<HarnessApproval> findApproval(String tenantId, String id) {
        return Optional.ofNullable(approvalMapper.selectOne(new LambdaQueryWrapper<HarnessApproval>()
                .eq(HarnessApproval::getId, id)
                .eq(HarnessApproval::getTenantId, tenantId)
                .eq(HarnessApproval::getDeleted, false)));
    }

    @Override
    public Optional<HarnessApproval> findApprovalByDecisionId(String runId, String decisionId) {
        if (decisionId == null || decisionId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(approvalMapper.selectOne(new LambdaQueryWrapper<HarnessApproval>()
                .eq(HarnessApproval::getRunId, runId)
                .eq(HarnessApproval::getDecisionId, decisionId)
                .eq(HarnessApproval::getDeleted, false)
                .last("LIMIT 1")));
    }

    @Override
    public boolean updateApproval(HarnessApproval approval, long expectedVersion) {
        int next = (int) expectedVersion + 1;
        int rows = approvalMapper.update(null, new LambdaUpdateWrapper<HarnessApproval>()
                .eq(HarnessApproval::getId, approval.getId())
                .eq(HarnessApproval::getTenantId, approval.getTenantId())
                .eq(HarnessApproval::getVersion, (int) expectedVersion)
                .eq(HarnessApproval::getDeleted, false)
                .set(HarnessApproval::getState, approval.getState())
                .set(HarnessApproval::getDecision, approval.getDecision())
                .set(HarnessApproval::getDecisionId, approval.getDecisionId())
                .set(HarnessApproval::getDecidedBy, approval.getDecidedBy())
                .set(HarnessApproval::getDecidedAt, approval.getDecidedAt())
                .set(HarnessApproval::getNote, approval.getNote())
                .set(HarnessApproval::getVersion, next)
                .set(HarnessApproval::getUpdatedTime, OffsetDateTime.now()));
        if (rows == 1) {
            approval.setVersion(next);
            return true;
        }
        return false;
    }

    // ===== Plan =====

    @Override
    public HarnessPlan insertPlan(HarnessPlan plan) {
        planMapper.insert(plan);
        return plan;
    }

    @Override
    public Optional<HarnessPlan> findPlan(String tenantId, String id) {
        return Optional.ofNullable(planMapper.selectOne(new LambdaQueryWrapper<HarnessPlan>()
                .eq(HarnessPlan::getId, id)
                .eq(HarnessPlan::getTenantId, tenantId)
                .eq(HarnessPlan::getDeleted, false)));
    }

    @Override
    public Optional<HarnessPlan> findPlanByIdempotencyKey(String runId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(planMapper.selectOne(new LambdaQueryWrapper<HarnessPlan>()
                .eq(HarnessPlan::getRunId, runId)
                .eq(HarnessPlan::getIdempotencyKey, idempotencyKey)
                .eq(HarnessPlan::getDeleted, false)
                .last("LIMIT 1")));
    }

    @Override
    public Optional<HarnessPlan> findLatestPlan(String runId) {
        return Optional.ofNullable(planMapper.selectOne(new LambdaQueryWrapper<HarnessPlan>()
                .eq(HarnessPlan::getRunId, runId)
                .eq(HarnessPlan::getDeleted, false)
                .orderByDesc(HarnessPlan::getCreatedTime)
                .last("LIMIT 1")));
    }

    @Override
    public boolean updatePlan(HarnessPlan plan, long expectedVersion) {
        int next = (int) expectedVersion + 1;
        int rows = planMapper.update(null, new LambdaUpdateWrapper<HarnessPlan>()
                .eq(HarnessPlan::getId, plan.getId())
                .eq(HarnessPlan::getTenantId, plan.getTenantId())
                .eq(HarnessPlan::getVersion, (int) expectedVersion)
                .eq(HarnessPlan::getDeleted, false)
                .set(HarnessPlan::getMode, plan.getMode())
                .set(HarnessPlan::getReviewState, plan.getReviewState())
                .set(HarnessPlan::getPlanMd, plan.getPlanMd())
                .set(HarnessPlan::getStepsJson, plan.getStepsJson())
                .set(HarnessPlan::getCanonicalHash, plan.getCanonicalHash())
                .set(HarnessPlan::getFeedback, plan.getFeedback())
                .set(HarnessPlan::getExpectedRevision, plan.getExpectedRevision())
                .set(HarnessPlan::getIdempotencyKey, plan.getIdempotencyKey())
                .set(HarnessPlan::getApprovedBy, plan.getApprovedBy())
                .set(HarnessPlan::getApprovedAt, plan.getApprovedAt())
                .set(HarnessPlan::getVersion, next)
                .set(HarnessPlan::getUpdatedTime, OffsetDateTime.now()));
        if (rows == 1) {
            plan.setVersion(next);
            return true;
        }
        return false;
    }

    /** 供 Service 在乐观锁失败时抛出统一错误。 */
    public static void requireUpdated(boolean ok, String resource, String id) {
        if (!ok) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK,
                    resource + " 乐观锁冲突或不存在: " + id);
        }
    }

    public static <T> T requirePresent(Optional<T> opt, String resource, String id) {
        return opt.orElseThrow(() -> new ResourceNotFoundException(resource + " 不存在: " + id));
    }
}
