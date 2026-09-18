package com.yutong.ai.harness.store;

import com.yutong.ai.harness.domain.HarnessApproval;
import com.yutong.ai.harness.domain.HarnessEvent;
import com.yutong.ai.harness.domain.HarnessPlan;
import com.yutong.ai.harness.domain.HarnessRun;
import com.yutong.ai.harness.domain.HarnessSession;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Harness 持久化契约。业务 revision 乐观锁：update* 成功时 revision+1。
 * 实现: {@link HarnessStoreService}（MyBatis-Plus）。
 */
public interface HarnessStore {

    // ===== Session =====

    HarnessSession insertSession(HarnessSession session);

    Optional<HarnessSession> findSession(String tenantId, String id);

    Optional<HarnessSession> findSession(String tenantId, String id, boolean includeDeleted);

    Optional<HarnessSession> findSessionByIdempotencyKey(String tenantId, String userId, String idempotencyKey);

    List<HarnessSession> listSessions(String tenantId, String userId, boolean includeDeleted);

    /** revision 乐观锁更新；返回 false 表示冲突。 */
    boolean updateSession(HarnessSession session, long expectedRevision);

    boolean softDeleteSession(String tenantId, String id);

    /** 按租户+用户恢复已删会话；userId 为空则仅按租户。 */
    boolean restoreSession(String tenantId, String id, String userId);

    boolean pinSession(String tenantId, String id, OffsetDateTime pinnedAt, long expectedRevision);

    // ===== Run =====

    HarnessRun insertRun(HarnessRun run);

    Optional<HarnessRun> findRun(String tenantId, String id);

    Optional<HarnessRun> findRunByIdempotencyKey(String sessionId, String idempotencyKey);

    List<HarnessRun> listRuns(String tenantId, String sessionId);

    boolean updateRun(HarnessRun run, long expectedRevision);

    // ===== Event =====

    /** 分配下一 sequence（run 内单调）。 */
    long nextEventSequence(String runId);

    HarnessEvent insertEvent(HarnessEvent event);

    List<HarnessEvent> listEvents(String runId, long afterSequence, int limit);

    // ===== Approval =====

    HarnessApproval insertApproval(HarnessApproval approval);

    Optional<HarnessApproval> findApproval(String tenantId, String id);

    Optional<HarnessApproval> findApprovalByDecisionId(String runId, String decisionId);

    boolean updateApproval(HarnessApproval approval, long expectedVersion);

    // ===== Plan =====

    HarnessPlan insertPlan(HarnessPlan plan);

    Optional<HarnessPlan> findPlan(String tenantId, String id);

    Optional<HarnessPlan> findPlanByIdempotencyKey(String runId, String idempotencyKey);

    Optional<HarnessPlan> findLatestPlan(String runId);

    boolean updatePlan(HarnessPlan plan, long expectedVersion);
}
