package com.yutong.ai.harness.approval;

import com.yutong.ai.harness.domain.HarnessApproval;
import com.yutong.ai.harness.domain.HarnessRun;
import com.yutong.ai.harness.enums.ApprovalState;
import com.yutong.ai.harness.enums.RunStatus;
import com.yutong.ai.harness.store.InMemoryHarnessStore;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ApprovalService：sha256 / decisionId 幂等 / 状态机 claim。
 */
@DisplayName("ApprovalService")
class ApprovalServiceTest {

    private static final String TENANT = "t1";
    private static final String USER = "u1";
    private static final String SESSION = "s1";
    private static final String RUN = "r1";

    private InMemoryHarnessStore store;
    private ApprovalService service;

    @BeforeEach
    void setUp() {
        CurrentUserContext.set(USER, TENANT, "tester");
        store = new InMemoryHarnessStore();
        service = new ApprovalService(store);
        seedRun();
    }

    @AfterEach
    void tearDown() {
        CurrentUserContext.clear();
    }

    private void seedRun() {
        HarnessRun run = new HarnessRun();
        run.setId(RUN);
        run.setTenantId(TENANT);
        run.setSessionId(SESSION);
        run.setUserId(USER);
        run.setStatus(RunStatus.WAITING_FOR_APPROVAL.name());
        run.setPermissionMode("WORKSPACE_WRITE");
        run.setPermissionRevision(3L);
        run.setRequirement("req");
        run.setRevision(0L);
        run.setDeleted(false);
        run.setVersion(0);
        store.insertRun(run);
    }

    @Test
    @DisplayName("createApproval 计算 argumentsSha256")
    void createApprovalComputesSha256() {
        String args = "{\"path\":\"a.txt\"}";
        HarnessApproval a = service.createApproval(SESSION, RUN, "write_file", "call-1", args, 3L, 0L);
        assertEquals(ApprovalState.PENDING.name(), a.getState());
        assertEquals(CanonicalHashes.sha256Hex(args), a.getArgumentsSha256());
        assertEquals(64, a.getArgumentsSha256().length());
    }

    @Test
    @DisplayName("sha256Hex 稳定且可复现")
    void sha256Stable() {
        String h1 = CanonicalHashes.sha256Hex("hello");
        String h2 = CanonicalHashes.sha256Hex("hello");
        assertEquals(h1, h2);
        assertNotEquals(h1, CanonicalHashes.sha256Hex("hello!"));
        // 空串与 null 一致
        assertEquals(CanonicalHashes.sha256Hex(""), CanonicalHashes.sha256Hex(null));
    }

    @Test
    @DisplayName("constantTimeEquals")
    void constantTimeEquals() {
        assertTrue(CanonicalHashes.constantTimeEquals("abc", "abc"));
        assertTrue(CanonicalHashes.constantTimeEquals(null, null));
        assertEquals(false, CanonicalHashes.constantTimeEquals("abc", "abd"));
        assertEquals(false, CanonicalHashes.constantTimeEquals("abc", null));
    }

    @Test
    @DisplayName("resolve 成功：APPROVE → APPROVED")
    void resolveApprove() {
        String args = "{\"n\":1}";
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", args, 3L, 0L);
        HarnessApproval resolved = service.resolve(a.getId(), "APPROVE", 0L,
                CanonicalHashes.sha256Hex(args), "dec-1");
        assertEquals(ApprovalState.APPROVED.name(), resolved.getState());
        assertEquals("APPROVE", resolved.getDecision());
        assertEquals("dec-1", resolved.getDecisionId());
    }

    @Test
    @DisplayName("resolve 拒绝错误 sha256（fail-closed）")
    void resolveRejectsBadHash() {
        String args = "{\"n\":1}";
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", args, 3L, 0L);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.resolve(a.getId(), "APPROVE", 0L,
                        CanonicalHashes.sha256Hex("{\"n\":2}"), "dec-1"));
        assertEquals("AI-403001", ex.errorCode().code());
    }

    @Test
    @DisplayName("resolve 校验 expectedRevision")
    void resolveChecksRevision() {
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", "{}", 3L, 0L);
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.resolve(a.getId(), "APPROVE", 99L,
                        CanonicalHashes.sha256Hex("{}"), "dec-1"));
        assertEquals("SYS-409001", ex.errorCode().code());
    }

    @Test
    @DisplayName("resolve decisionId 幂等：同 decisionId 重复调用返回既有结果")
    void resolveIdempotentDecisionId() {
        String args = "{\"a\":1}";
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", args, 3L, 0L);
        HarnessApproval first = service.resolve(a.getId(), "APPROVE", 0L,
                CanonicalHashes.sha256Hex(args), "same-dec");
        // 已 APPROVED，同 decisionId 再 resolve 直接返回
        HarnessApproval second = service.resolve(a.getId(), "APPROVE", first.getVersion(),
                CanonicalHashes.sha256Hex(args), "same-dec");
        assertEquals(first.getDecisionId(), second.getDecisionId());
        assertEquals(ApprovalState.APPROVED.name(), second.getState());
    }

    @Test
    @DisplayName("resolve 已终态不可重复改判")
    void resolveTerminalRejected() {
        String args = "{}";
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", args, 3L, 0L);
        service.resolve(a.getId(), "DENY", 0L, CanonicalHashes.sha256Hex(args), "d1");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.resolve(a.getId(), "APPROVE", 1L,
                        CanonicalHashes.sha256Hex(args), "d2"));
        assertEquals("SYS-409004", ex.errorCode().code());
    }

    @Test
    @DisplayName("claim：APPROVED → CONSUMED，且仅一次")
    void claimOnce() {
        String args = "{\"x\":true}";
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", args, 3L, 0L);
        service.resolve(a.getId(), "APPROVE", 0L, CanonicalHashes.sha256Hex(args), "d-claim");
        HarnessApproval consumed = service.claim(RUN, a.getId());
        assertEquals(ApprovalState.CONSUMED.name(), consumed.getState());
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.claim(RUN, a.getId()));
        assertEquals("SYS-409004", ex.errorCode().code());
    }

    @Test
    @DisplayName("claim 校验 permissionRevision 与 run 一致")
    void claimPermissionRevisionMismatch() {
        String args = "{}";
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", args, 3L, 0L);
        service.resolve(a.getId(), "APPROVE", 0L, CanonicalHashes.sha256Hex(args), "d-perm");
        // 模拟权限版本变更
        HarnessRun run = store.findRun(TENANT, RUN).orElseThrow();
        run.setPermissionRevision(99L);
        store.mutateRun(run);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.claim(RUN, a.getId()));
        assertEquals("SYS-409004", ex.errorCode().code());
        assertTrue(ex.getMessage().contains("权限版本"));
    }

    @Test
    @DisplayName("PENDING 不可 claim")
    void claimPendingRejected() {
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", "{}", 3L, 0L);
        assertThrows(BusinessException.class, () -> service.claim(RUN, a.getId()));
    }

    @Test
    @DisplayName("resolve 落库 note")
    void resolvePersistsNote() {
        String args = "{\"k\":1}";
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", args, 0L, 0L);
        HarnessApproval resolved = service.resolve(a.getId(), "APPROVE", 0L,
                CanonicalHashes.sha256Hex(args), "dec-note", "人工批准：允许执行");
        assertEquals("人工批准：允许执行", resolved.getNote());
    }

    @Test
    @DisplayName("expires_at 已到 → resolve 迁 EXPIRED")
    void resolveExpiresWhenDue() {
        String args = "{}";
        HarnessApproval a = service.createApproval(SESSION, RUN, "exec", "c1", args, 0L, 0L);
        a.setExpiresAt(java.time.OffsetDateTime.now().minusMinutes(1));
        store.updateApproval(a, a.getVersion() == null ? 0L : a.getVersion());
        BusinessException ex = assertThrows(BusinessException.class, () ->
                service.resolve(a.getId(), "APPROVE", 1L,
                        CanonicalHashes.sha256Hex(args), "dec-exp"));
        assertTrue(ex.getMessage().contains("终态") || ex.getMessage().contains("EXPIRED"));
        HarnessApproval after = store.findApproval(TENANT, a.getId()).orElseThrow();
        assertEquals(ApprovalState.EXPIRED.name(), after.getState());
    }
}
