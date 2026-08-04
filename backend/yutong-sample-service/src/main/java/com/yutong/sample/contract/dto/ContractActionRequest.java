package com.yutong.sample.contract.dto;

import lombok.Data;

/**
 * 合同状态流转操作请求。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 *
 * <p>action 取值:
 * <ul>
 *   <li>SUBMIT 提交审批 (DRAFT → SUBMITTED)</li>
 *   <li>APPROVE 审批通过 (SUBMITTED → APPROVED)</li>
 *   <li>REJECT 驳回 (SUBMITTED → REJECTED)</li>
 *   <li>RESUBMIT 重新提交 (REJECTED → SUBMITTED)</li>
 *   <li>SIGN 签订 (APPROVED → SIGNED)</li>
 *   <li>ARCHIVE 归档 (SIGNED → ARCHIVED)</li>
 *   <li>CANCEL 取消 (任意非终态 → CANCELLED)</li>
 * </ul>
 */
@Data
public class ContractActionRequest {

    /** 操作动作 */
    private String action;

    /** 审批意见（APPROVE/REJECT 必填，其他可选） */
    private String opinion;
}
