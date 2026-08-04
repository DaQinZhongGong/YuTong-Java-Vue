package com.yutong.sample.request.service;

import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.sample.request.domain.ApprovalRecord;
import com.yutong.sample.request.domain.BizRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 申请单状态机领域服务单元测试。设计来源: 18-样例业务详细设计 状态机
 *
 * 合法流转:
 *   DRAFT     → SUBMITTED (submit)
 *   REJECTED  → SUBMITTED (submit)
 *   SUBMITTED → APPROVED  (approve)
 *   SUBMITTED → REJECTED  (reject)
 *   SUBMITTED → DRAFT     (withdraw)
 *   APPROVED  → ARCHIVED  (archive)
 *
 * 非法流转: 上述以外的任意组合。
 */
@DisplayName("申请单状态机领域服务")
class BizRequestDomainServiceTest {

    private final BizRequestDomainService domainService = new BizRequestDomainService();

    // ==================== nextStatus ====================

    @Nested
    @DisplayName("nextStatus: 动作映射到目标状态")
    class NextStatus {

        @Test
        @DisplayName("SUBMIT → SUBMITTED")
        void submitReturnsSubmitted() {
            assertEquals(BizRequest.STATUS_SUBMITTED,
                    domainService.nextStatus(ApprovalRecord.ACTION_SUBMIT));
        }

        @Test
        @DisplayName("APPROVE → APPROVED")
        void approveReturnsApproved() {
            assertEquals(BizRequest.STATUS_APPROVED,
                    domainService.nextStatus(ApprovalRecord.ACTION_APPROVE));
        }

        @Test
        @DisplayName("REJECT → REJECTED")
        void rejectReturnsRejected() {
            assertEquals(BizRequest.STATUS_REJECTED,
                    domainService.nextStatus(ApprovalRecord.ACTION_REJECT));
        }

        @Test
        @DisplayName("WITHDRAW → DRAFT")
        void withdrawReturnsDraft() {
            assertEquals(BizRequest.STATUS_DRAFT,
                    domainService.nextStatus(ApprovalRecord.ACTION_WITHDRAW));
        }

        @Test
        @DisplayName("ARCHIVE → ARCHIVED")
        void archiveReturnsArchived() {
            assertEquals(BizRequest.STATUS_ARCHIVED,
                    domainService.nextStatus(ApprovalRecord.ACTION_ARCHIVE));
        }

        @Test
        @DisplayName("未知动作抛 BusinessConflictException")
        void unknownActionThrows() {
            BusinessConflictException ex = assertThrows(BusinessConflictException.class,
                    () -> domainService.nextStatus("UNKNOWN"));
            assertTrue(ex.getMessage().contains("不支持的动作"));
        }

        @Test
        @DisplayName("null 动作抛 BusinessConflictException")
        void nullActionThrows() {
            assertThrows(BusinessConflictException.class,
                    () -> domainService.nextStatus(null));
        }
    }

    // ==================== validateTransition (合法) ====================

    @Nested
    @DisplayName("validateTransition: 合法流转不抛异常")
    class LegalTransitions {

        @Test
        @DisplayName("DRAFT + SUBMIT 合法")
        void draftToSubmitted() {
            assertDoesNotThrow(() ->
                    domainService.validateTransition(BizRequest.STATUS_DRAFT, ApprovalRecord.ACTION_SUBMIT));
        }

        @Test
        @DisplayName("REJECTED + SUBMIT 合法 (驳回后重新提交)")
        void rejectedToSubmitted() {
            assertDoesNotThrow(() ->
                    domainService.validateTransition(BizRequest.STATUS_REJECTED, ApprovalRecord.ACTION_SUBMIT));
        }

        @Test
        @DisplayName("SUBMITTED + APPROVE 合法")
        void submittedToApproved() {
            assertDoesNotThrow(() ->
                    domainService.validateTransition(BizRequest.STATUS_SUBMITTED, ApprovalRecord.ACTION_APPROVE));
        }

        @Test
        @DisplayName("SUBMITTED + REJECT 合法")
        void submittedToRejected() {
            assertDoesNotThrow(() ->
                    domainService.validateTransition(BizRequest.STATUS_SUBMITTED, ApprovalRecord.ACTION_REJECT));
        }

        @Test
        @DisplayName("SUBMITTED + WITHDRAW 合法")
        void submittedToDraft() {
            assertDoesNotThrow(() ->
                    domainService.validateTransition(BizRequest.STATUS_SUBMITTED, ApprovalRecord.ACTION_WITHDRAW));
        }

        @Test
        @DisplayName("APPROVED + ARCHIVE 合法")
        void approvedToArchived() {
            assertDoesNotThrow(() ->
                    domainService.validateTransition(BizRequest.STATUS_APPROVED, ApprovalRecord.ACTION_ARCHIVE));
        }
    }

    // ==================== validateTransition (非法) ====================

    @Nested
    @DisplayName("validateTransition: 非法流转抛异常")
    class IllegalTransitions {

        @Test
        @DisplayName("DRAFT + APPROVE 非法 (草稿不能直接审批)")
        void draftDirectApproveIllegal() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_DRAFT, ApprovalRecord.ACTION_APPROVE));
            assertTrue(ex.getMessage().contains("不允许执行"));
        }

        @Test
        @DisplayName("DRAFT + REJECT 非法")
        void draftRejectIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_DRAFT, ApprovalRecord.ACTION_REJECT));
        }

        @Test
        @DisplayName("DRAFT + WITHDRAW 非法")
        void draftWithdrawIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_DRAFT, ApprovalRecord.ACTION_WITHDRAW));
        }

        @Test
        @DisplayName("DRAFT + ARCHIVE 非法")
        void draftArchiveIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_DRAFT, ApprovalRecord.ACTION_ARCHIVE));
        }

        @Test
        @DisplayName("APPROVED + SUBMIT 非法 (已审批不可再提交)")
        void approvedSubmitIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_APPROVED, ApprovalRecord.ACTION_SUBMIT));
        }

        @Test
        @DisplayName("APPROVED + REJECT 非法")
        void approvedRejectIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_APPROVED, ApprovalRecord.ACTION_REJECT));
        }

        @Test
        @DisplayName("APPROVED + WITHDRAW 非法")
        void approvedWithdrawIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_APPROVED, ApprovalRecord.ACTION_WITHDRAW));
        }

        @Test
        @DisplayName("REJECTED + APPROVE 非法 (驳回不可直接审批)")
        void rejectedApproveIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_REJECTED, ApprovalRecord.ACTION_APPROVE));
        }

        @Test
        @DisplayName("REJECTED + REJECT 非法")
        void rejectedRejectIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_REJECTED, ApprovalRecord.ACTION_REJECT));
        }

        @Test
        @DisplayName("REJECTED + WITHDRAW 非法")
        void rejectedWithdrawIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_REJECTED, ApprovalRecord.ACTION_WITHDRAW));
        }

        @Test
        @DisplayName("REJECTED + ARCHIVE 非法")
        void rejectedArchiveIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_REJECTED, ApprovalRecord.ACTION_ARCHIVE));
        }

        @Test
        @DisplayName("ARCHIVED + SUBMIT 非法 (归档终态")
        void archivedSubmitIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_ARCHIVED, ApprovalRecord.ACTION_SUBMIT));
        }

        @Test
        @DisplayName("ARCHIVED + ARCHIVE 非法 (归档终态")
        void archivedArchiveIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_ARCHIVED, ApprovalRecord.ACTION_ARCHIVE));
        }

        @Test
        @DisplayName("SUBMITTED + SUBMIT 非法 (不可重复提交)")
        void submittedSubmitIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_SUBMITTED, ApprovalRecord.ACTION_SUBMIT));
        }

        @Test
        @DisplayName("SUBMITTED + ARCHIVE 非法")
        void submittedArchiveIllegal() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_SUBMITTED, ApprovalRecord.ACTION_ARCHIVE));
        }

        @Test
        @DisplayName("未知动作抛 BusinessConflictException")
        void unknownActionThrows() {
            assertThrows(BusinessException.class,
                    () -> domainService.validateTransition(BizRequest.STATUS_DRAFT, "UNKNOWN"));
        }
    }
}
