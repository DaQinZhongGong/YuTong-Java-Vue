package com.yutong.sample.contract.service;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.sample.contract.domain.Contract;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 合同领域服务 - 状态机。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 *
 * <p>状态机:
 * <pre>
 *   DRAFT     → SUBMITTED  (SUBMIT 提交审批)
 *   SUBMITTED → APPROVED   (APPROVE 审批通过)
 *   SUBMITTED → REJECTED   (REJECT 驳回)
 *   REJECTED  → SUBMITTED  (RESUBMIT 重新提交)
 *   APPROVED  → SIGNED     (SIGN 签订)
 *   SIGNED    → ARCHIVED   (ARCHIVE 归档)
 *   DRAFT/SUBMITTED/APPROVED/REJECTED/SIGNED → CANCELLED (CANCEL 取消)
 * </pre>
 *
 * <p>终态: ARCHIVED（归档只读）、CANCELLED（取消）。
 */
@Service
public class ContractDomainService {

    /** 合法状态流转映射: action → {fromStatus → toStatus} */
    private static final Map<String, Map<String, String>> TRANSITIONS = Map.of(
            "SUBMIT", Map.of(Contract.STATUS_DRAFT, Contract.STATUS_SUBMITTED),
            "APPROVE", Map.of(Contract.STATUS_SUBMITTED, Contract.STATUS_APPROVED),
            "REJECT", Map.of(Contract.STATUS_SUBMITTED, Contract.STATUS_REJECTED),
            "RESUBMIT", Map.of(Contract.STATUS_REJECTED, Contract.STATUS_SUBMITTED),
            "SIGN", Map.of(Contract.STATUS_APPROVED, Contract.STATUS_SIGNED),
            "ARCHIVE", Map.of(Contract.STATUS_SIGNED, Contract.STATUS_ARCHIVED),
            "CANCEL", Map.of(
                    Contract.STATUS_DRAFT, Contract.STATUS_CANCELLED,
                    Contract.STATUS_SUBMITTED, Contract.STATUS_CANCELLED,
                    Contract.STATUS_APPROVED, Contract.STATUS_CANCELLED,
                    Contract.STATUS_REJECTED, Contract.STATUS_CANCELLED,
                    Contract.STATUS_SIGNED, Contract.STATUS_CANCELLED)
    );

    /** 校验状态流转是否合法，返回目标状态。 */
    public String validateTransition(String action, String fromStatus) {
        Map<String, String> actionMap = TRANSITIONS.get(action);
        if (actionMap == null) {
            throw new BusinessException(ErrorCode.CTR_CONTRACT_STATUS_NOT_ALLOWED,
                    "不支持的操作: " + action);
        }
        String toStatus = actionMap.get(fromStatus);
        if (toStatus == null) {
            throw new BusinessException(ErrorCode.CTR_CONTRACT_STATUS_NOT_ALLOWED,
                    String.format("合同状态 %s 不允许执行 %s 操作", fromStatus, action));
        }
        return toStatus;
    }

    /** 获取所有合法操作动作 */
    public Set<String> getValidActions() {
        return TRANSITIONS.keySet();
    }

    /** 判断指定状态下合同是否可编辑（ARCHIVED/CANCELLED 不可编辑） */
    public boolean isEditable(String status) {
        return !Contract.STATUS_ARCHIVED.equals(status)
                && !Contract.STATUS_CANCELLED.equals(status);
    }
}
