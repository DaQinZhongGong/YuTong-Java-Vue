package com.yutong.sample.request.service;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.sample.request.domain.ApprovalRecord;
import com.yutong.sample.request.domain.BizRequest;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 申请单状态机领域服务。设计来源: 18-样例业务详细设计 状态机
 * 纯领域逻辑，不涉及数据库操作。
 *
 * 状态流转:
 * DRAFT → SUBMITTED (submit)
 * SUBMITTED → APPROVED (approve) / REJECTED (reject) / DRAFT (withdraw)
 * REJECTED → DRAFT (saveDraft 编辑回写，非本服务动作)
 * APPROVED → ARCHIVED (archive)
 */
@Service
public class BizRequestDomainService {

    /** 动作 → 目标状态 */
    private static final Map<String, String> ACTION_TARGET = Map.of(
            ApprovalRecord.ACTION_SUBMIT, BizRequest.STATUS_SUBMITTED,
            ApprovalRecord.ACTION_APPROVE, BizRequest.STATUS_APPROVED,
            ApprovalRecord.ACTION_REJECT, BizRequest.STATUS_REJECTED,
            ApprovalRecord.ACTION_WITHDRAW, BizRequest.STATUS_DRAFT,
            ApprovalRecord.ACTION_ARCHIVE, BizRequest.STATUS_ARCHIVED
    );

    /** 动作 → 允许的源状态集合 */
    private static final Map<String, Set<String>> ALLOWED_SOURCES = Map.of(
            ApprovalRecord.ACTION_SUBMIT, Set.of(BizRequest.STATUS_DRAFT, BizRequest.STATUS_REJECTED),
            ApprovalRecord.ACTION_APPROVE, Set.of(BizRequest.STATUS_SUBMITTED),
            ApprovalRecord.ACTION_REJECT, Set.of(BizRequest.STATUS_SUBMITTED),
            ApprovalRecord.ACTION_WITHDRAW, Set.of(BizRequest.STATUS_SUBMITTED),
            ApprovalRecord.ACTION_ARCHIVE, Set.of(BizRequest.STATUS_APPROVED)
    );

    /**
     * 动作 → 状态流转不允许时的错误码。
     * GA2-L189: 对齐 openapi.yaml x-error-codes (BIZ-409001 submitNotAllowed / BIZ-409002 approvalNotAllowed / BIZ-409004 withdrawNotAllowed)。
     */
    private static final Map<String, ErrorCode> ACTION_STATUS_ERROR_CODE = Map.of(
            ApprovalRecord.ACTION_SUBMIT, ErrorCode.BIZ_REQUEST_STATUS_SUBMIT_NOT_ALLOWED,
            ApprovalRecord.ACTION_APPROVE, ErrorCode.BIZ_REQUEST_STATUS_APPROVE_NOT_ALLOWED,
            ApprovalRecord.ACTION_REJECT, ErrorCode.BIZ_REQUEST_STATUS_APPROVE_NOT_ALLOWED,
            ApprovalRecord.ACTION_WITHDRAW, ErrorCode.BIZ_REQUEST_STATUS_WITHDRAW_NOT_ALLOWED,
            ApprovalRecord.ACTION_ARCHIVE, ErrorCode.BIZ_REQUEST_STATUS_SUBMIT_NOT_ALLOWED
    );

    /**
     * 返回动作对应的新状态。
     *
     * @param action 动作枚举值 SUBMIT/APPROVE/REJECT/WITHDRAW/ARCHIVE
     * @return 目标状态码
     */
    public String nextStatus(String action) {
        if (action == null) {
            throw new BusinessConflictException("不支持的动作: null");
        }
        String target = ACTION_TARGET.get(action);
        if (target == null) {
            throw new BusinessConflictException("不支持的动作: " + action);
        }
        return target;
    }

    /**
     * 校验状态流转合法性，非法抛 action 专属错误码的 BusinessException。
     * GA2-L189: 使用 ACTION_STATUS_ERROR_CODE 映射，对齐 openapi.yaml x-error-codes。
     *
     * @param currentStatus 当前状态
     * @param action        动作
     */
    public void validateTransition(String currentStatus, String action) {
        if (action == null) {
            throw new BusinessConflictException("不支持的动作: null");
        }
        Set<String> allowed = ALLOWED_SOURCES.get(action);
        if (allowed == null) {
            throw new BusinessConflictException("不支持的动作: " + action);
        }
        if (!allowed.contains(currentStatus)) {
            ErrorCode ec = ACTION_STATUS_ERROR_CODE.getOrDefault(action, ErrorCode.SYS_BUSINESS_CONFLICT);
            throw new BusinessException(ec,
                    "申请单当前状态[" + currentStatus + "]不允许执行[" + action + "]操作");
        }
    }
}
