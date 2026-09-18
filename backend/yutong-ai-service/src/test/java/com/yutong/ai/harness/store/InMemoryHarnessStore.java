package com.yutong.ai.harness.store;

import com.yutong.ai.harness.domain.HarnessApproval;
import com.yutong.ai.harness.domain.HarnessEvent;
import com.yutong.ai.harness.domain.HarnessPlan;
import com.yutong.ai.harness.domain.HarnessRun;
import com.yutong.ai.harness.domain.HarnessSession;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 内存版 HarnessStore，供 ApprovalService/纯逻辑单测使用（不 mock 框架对象）。
 */
public class InMemoryHarnessStore implements HarnessStore {

    private final Map<String, HarnessSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, HarnessRun> runs = new ConcurrentHashMap<>();
    private final Map<String, HarnessEvent> events = new ConcurrentHashMap<>();
    private final Map<String, HarnessApproval> approvals = new ConcurrentHashMap<>();
    private final Map<String, HarnessPlan> plans = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> sequences = new ConcurrentHashMap<>();

    // ===== Session =====

    @Override
    public HarnessSession insertSession(HarnessSession session) {
        if (session.getRevision() == null) {
            session.setRevision(0L);
        }
        sessions.put(session.getId(), copySession(session));
        return session;
    }

    @Override
    public Optional<HarnessSession> findSession(String tenantId, String id) {
        return findSession(tenantId, id, false);
    }

    @Override
    public Optional<HarnessSession> findSession(String tenantId, String id, boolean includeDeleted) {
        HarnessSession s = sessions.get(id);
        if (s == null || !s.getTenantId().equals(tenantId)) {
            return Optional.empty();
        }
        if (!includeDeleted && Boolean.TRUE.equals(s.getDeleted())) {
            return Optional.empty();
        }
        return Optional.of(copySession(s));
    }

    @Override
    public Optional<HarnessSession> findSessionByIdempotencyKey(String tenantId, String userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return sessions.values().stream()
                .filter(s -> s.getTenantId().equals(tenantId)
                        && s.getUserId().equals(userId)
                        && idempotencyKey.equals(s.getIdempotencyKey())
                        && !Boolean.TRUE.equals(s.getDeleted()))
                .findFirst()
                .map(this::copySession);
    }

    @Override
    public List<HarnessSession> listSessions(String tenantId, String userId, boolean includeDeleted) {
        return sessions.values().stream()
                .filter(s -> s.getTenantId().equals(tenantId) && s.getUserId().equals(userId))
                .filter(s -> includeDeleted || !Boolean.TRUE.equals(s.getDeleted()))
                .sorted(Comparator.comparing(HarnessSession::getCreatedTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::copySession)
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateSession(HarnessSession session, long expectedRevision) {
        HarnessSession cur = sessions.get(session.getId());
        if (cur == null || Boolean.TRUE.equals(cur.getDeleted())) {
            return false;
        }
        long rev = cur.getRevision() == null ? 0L : cur.getRevision();
        if (rev != expectedRevision) {
            return false;
        }
        HarnessSession next = copySession(session);
        next.setRevision(expectedRevision + 1);
        sessions.put(next.getId(), next);
        return true;
    }

    @Override
    public boolean softDeleteSession(String tenantId, String id) {
        HarnessSession s = sessions.get(id);
        if (s == null || !s.getTenantId().equals(tenantId) || Boolean.TRUE.equals(s.getDeleted())) {
            return false;
        }
        s.setDeleted(true);
        return true;
    }

    @Override
    public boolean restoreSession(String tenantId, String id, String userId) {
        HarnessSession s = sessions.get(id);
        if (s == null || !s.getTenantId().equals(tenantId) || !Boolean.TRUE.equals(s.getDeleted())) {
            return false;
        }
        if (userId != null && !userId.isBlank()
                && s.getUserId() != null && !s.getUserId().equals(userId)) {
            return false;
        }
        s.setDeleted(false);
        return true;
    }

    @Override
    public boolean pinSession(String tenantId, String id, OffsetDateTime pinnedAt, long expectedRevision) {
        HarnessSession s = sessions.get(id);
        if (s == null || !s.getTenantId().equals(tenantId) || Boolean.TRUE.equals(s.getDeleted())) {
            return false;
        }
        long rev = s.getRevision() == null ? 0L : s.getRevision();
        if (rev != expectedRevision) {
            return false;
        }
        s.setPinnedAt(pinnedAt);
        s.setRevision(expectedRevision + 1);
        return true;
    }

    // ===== Run =====

    @Override
    public HarnessRun insertRun(HarnessRun run) {
        if (run.getRevision() == null) {
            run.setRevision(0L);
        }
        runs.put(run.getId(), copyRun(run));
        return run;
    }

    @Override
    public Optional<HarnessRun> findRun(String tenantId, String id) {
        HarnessRun r = runs.get(id);
        if (r == null || !r.getTenantId().equals(tenantId) || Boolean.TRUE.equals(r.getDeleted())) {
            return Optional.empty();
        }
        return Optional.of(copyRun(r));
    }

    @Override
    public Optional<HarnessRun> findRunByIdempotencyKey(String sessionId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return runs.values().stream()
                .filter(r -> r.getSessionId().equals(sessionId)
                        && idempotencyKey.equals(r.getIdempotencyKey())
                        && !Boolean.TRUE.equals(r.getDeleted()))
                .findFirst()
                .map(this::copyRun);
    }

    @Override
    public List<HarnessRun> listRuns(String tenantId, String sessionId) {
        return runs.values().stream()
                .filter(r -> r.getSessionId().equals(sessionId)
                        && (tenantId == null || tenantId.equals(r.getTenantId()))
                        && !Boolean.TRUE.equals(r.getDeleted()))
                .sorted(Comparator.comparing(HarnessRun::getCreatedTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::copyRun)
                .collect(Collectors.toList());
    }

    @Override
    public boolean updateRun(HarnessRun run, long expectedRevision) {
        HarnessRun cur = runs.get(run.getId());
        if (cur == null || Boolean.TRUE.equals(cur.getDeleted())) {
            return false;
        }
        long rev = cur.getRevision() == null ? 0L : cur.getRevision();
        if (rev != expectedRevision) {
            return false;
        }
        HarnessRun next = copyRun(run);
        next.setRevision(expectedRevision + 1);
        runs.put(next.getId(), next);
        return true;
    }

    // ===== Event =====

    @Override
    public long nextEventSequence(String runId) {
        return sequences.computeIfAbsent(runId, k -> new AtomicLong(0)).incrementAndGet();
    }

    @Override
    public HarnessEvent insertEvent(HarnessEvent event) {
        events.put(event.getId(), event);
        return event;
    }

    @Override
    public List<HarnessEvent> listEvents(String runId, long afterSequence, int limit) {
        int size = limit <= 0 ? 200 : Math.min(limit, 1000);
        return events.values().stream()
                .filter(e -> e.getRunId().equals(runId))
                .filter(e -> e.getSequenceNo() != null && e.getSequenceNo() > afterSequence)
                .sorted(Comparator.comparing(HarnessEvent::getSequenceNo))
                .limit(size)
                .collect(Collectors.toList());
    }

    // ===== Approval =====

    @Override
    public HarnessApproval insertApproval(HarnessApproval approval) {
        if (approval.getVersion() == null) {
            approval.setVersion(0);
        }
        approvals.put(approval.getId(), copyApproval(approval));
        return approval;
    }

    @Override
    public Optional<HarnessApproval> findApproval(String tenantId, String id) {
        HarnessApproval a = approvals.get(id);
        if (a == null || !a.getTenantId().equals(tenantId) || Boolean.TRUE.equals(a.getDeleted())) {
            return Optional.empty();
        }
        return Optional.of(copyApproval(a));
    }

    @Override
    public Optional<HarnessApproval> findApprovalByDecisionId(String runId, String decisionId) {
        if (runId == null || decisionId == null || decisionId.isBlank()) {
            return Optional.empty();
        }
        return approvals.values().stream()
                .filter(a -> runId.equals(a.getRunId())
                        && decisionId.equals(a.getDecisionId())
                        && !Boolean.TRUE.equals(a.getDeleted()))
                .findFirst()
                .map(this::copyApproval);
    }

    @Override
    public boolean updateApproval(HarnessApproval approval, long expectedVersion) {
        HarnessApproval cur = approvals.get(approval.getId());
        if (cur == null || Boolean.TRUE.equals(cur.getDeleted())) {
            return false;
        }
        int ver = cur.getVersion() == null ? 0 : cur.getVersion();
        if (ver != expectedVersion) {
            return false;
        }
        HarnessApproval next = copyApproval(approval);
        next.setVersion((int) expectedVersion + 1);
        approvals.put(next.getId(), next);
        return true;
    }

    // ===== Plan =====

    @Override
    public HarnessPlan insertPlan(HarnessPlan plan) {
        if (plan.getVersion() == null) {
            plan.setVersion(0);
        }
        plans.put(plan.getId(), copyPlan(plan));
        return plan;
    }

    @Override
    public Optional<HarnessPlan> findPlan(String tenantId, String id) {
        HarnessPlan p = plans.get(id);
        if (p == null || !p.getTenantId().equals(tenantId) || Boolean.TRUE.equals(p.getDeleted())) {
            return Optional.empty();
        }
        return Optional.of(copyPlan(p));
    }

    @Override
    public Optional<HarnessPlan> findPlanByIdempotencyKey(String runId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return plans.values().stream()
                .filter(p -> p.getRunId().equals(runId)
                        && idempotencyKey.equals(p.getIdempotencyKey())
                        && !Boolean.TRUE.equals(p.getDeleted()))
                .findFirst()
                .map(this::copyPlan);
    }

    @Override
    public Optional<HarnessPlan> findLatestPlan(String runId) {
        return plans.values().stream()
                .filter(p -> p.getRunId().equals(runId) && !Boolean.TRUE.equals(p.getDeleted()))
                .max(Comparator.comparing(HarnessPlan::getCreatedTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(this::copyPlan);
    }

    @Override
    public boolean updatePlan(HarnessPlan plan, long expectedVersion) {
        HarnessPlan cur = plans.get(plan.getId());
        if (cur == null || Boolean.TRUE.equals(cur.getDeleted())) {
            return false;
        }
        int ver = cur.getVersion() == null ? 0 : cur.getVersion();
        if (ver != expectedVersion) {
            return false;
        }
        HarnessPlan next = copyPlan(plan);
        next.setVersion((int) expectedVersion + 1);
        plans.put(next.getId(), next);
        return true;
    }

    // ===== copy helpers =====

    private HarnessSession copySession(HarnessSession s) {
        HarnessSession c = new HarnessSession();
        c.setId(s.getId());
        c.setTenantId(s.getTenantId());
        c.setCreatedBy(s.getCreatedBy());
        c.setCreatedTime(s.getCreatedTime());
        c.setUpdatedBy(s.getUpdatedBy());
        c.setUpdatedTime(s.getUpdatedTime());
        c.setDeleted(s.getDeleted());
        c.setVersion(s.getVersion());
        c.setRemark(s.getRemark());
        c.setUserId(s.getUserId());
        c.setTitle(s.getTitle());
        c.setWorkspacePath(s.getWorkspacePath());
        c.setWorkspaceManifest(s.getWorkspaceManifest());
        c.setModel(s.getModel());
        c.setPermissionMode(s.getPermissionMode());
        c.setApprovalPolicy(s.getApprovalPolicy());
        c.setThinkingLevel(s.getThinkingLevel());
        c.setVerificationMode(s.getVerificationMode());
        c.setActiveRunId(s.getActiveRunId());
        c.setIdempotencyKey(s.getIdempotencyKey());
        c.setPinnedAt(s.getPinnedAt());
        c.setRevision(s.getRevision());
        return c;
    }

    private HarnessRun copyRun(HarnessRun s) {
        HarnessRun c = new HarnessRun();
        c.setId(s.getId());
        c.setTenantId(s.getTenantId());
        c.setCreatedBy(s.getCreatedBy());
        c.setCreatedTime(s.getCreatedTime());
        c.setUpdatedBy(s.getUpdatedBy());
        c.setUpdatedTime(s.getUpdatedTime());
        c.setDeleted(s.getDeleted());
        c.setVersion(s.getVersion());
        c.setRemark(s.getRemark());
        c.setSessionId(s.getSessionId());
        c.setUserId(s.getUserId());
        c.setStatus(s.getStatus());
        c.setRequirement(s.getRequirement());
        c.setPermissionMode(s.getPermissionMode());
        c.setPermissionRevision(s.getPermissionRevision());
        c.setBudgetJson(s.getBudgetJson());
        c.setUsageJson(s.getUsageJson());
        c.setPlanJson(s.getPlanJson());
        c.setIteration(s.getIteration());
        c.setToolCallCount(s.getToolCallCount());
        c.setCancelRequested(s.getCancelRequested());
        c.setIdempotencyKey(s.getIdempotencyKey());
        c.setErrorMessage(s.getErrorMessage());
        c.setRevision(s.getRevision());
        return c;
    }

    private HarnessApproval copyApproval(HarnessApproval s) {
        HarnessApproval c = new HarnessApproval();
        c.setId(s.getId());
        c.setTenantId(s.getTenantId());
        c.setCreatedBy(s.getCreatedBy());
        c.setCreatedTime(s.getCreatedTime());
        c.setUpdatedBy(s.getUpdatedBy());
        c.setUpdatedTime(s.getUpdatedTime());
        c.setDeleted(s.getDeleted());
        c.setVersion(s.getVersion());
        c.setRemark(s.getRemark());
        c.setSessionId(s.getSessionId());
        c.setRunId(s.getRunId());
        c.setToolName(s.getToolName());
        c.setToolCallId(s.getToolCallId());
        c.setArgumentsJson(s.getArgumentsJson());
        c.setArgumentsSha256(s.getArgumentsSha256());
        c.setState(s.getState());
        c.setExpectedRevision(s.getExpectedRevision());
        c.setPermissionRevision(s.getPermissionRevision());
        c.setDecisionId(s.getDecisionId());
        c.setDecision(s.getDecision());
        c.setDecidedBy(s.getDecidedBy());
        c.setDecidedAt(s.getDecidedAt());
        c.setNote(s.getNote());
        c.setExpiresAt(s.getExpiresAt());
        c.setVersion(s.getVersion());
        return c;
    }

    private HarnessPlan copyPlan(HarnessPlan s) {
        HarnessPlan c = new HarnessPlan();
        c.setId(s.getId());
        c.setTenantId(s.getTenantId());
        c.setCreatedBy(s.getCreatedBy());
        c.setCreatedTime(s.getCreatedTime());
        c.setUpdatedBy(s.getUpdatedBy());
        c.setUpdatedTime(s.getUpdatedTime());
        c.setDeleted(s.getDeleted());
        c.setVersion(s.getVersion());
        c.setRemark(s.getRemark());
        c.setSessionId(s.getSessionId());
        c.setRunId(s.getRunId());
        c.setTaskId(s.getTaskId());
        c.setMode(s.getMode());
        c.setReviewState(s.getReviewState());
        c.setPlanMd(s.getPlanMd());
        c.setStepsJson(s.getStepsJson());
        c.setCanonicalHash(s.getCanonicalHash());
        c.setFeedback(s.getFeedback());
        c.setExpectedRevision(s.getExpectedRevision());
        c.setIdempotencyKey(s.getIdempotencyKey());
        c.setApprovedBy(s.getApprovedBy());
        c.setApprovedAt(s.getApprovedAt());
        c.setVersion(s.getVersion());
        return c;
    }

    /** 测试辅助：直接改内部 run（模拟并发/权限版本变更）。 */
    public void mutateRun(HarnessRun run) {
        runs.put(run.getId(), copyRun(run));
    }

    public List<HarnessEvent> allEvents() {
        return new ArrayList<>(events.values());
    }
}
