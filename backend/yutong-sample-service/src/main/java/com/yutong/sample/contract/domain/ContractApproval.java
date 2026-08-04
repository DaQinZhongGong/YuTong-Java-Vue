package com.yutong.sample.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 合同审批记录。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 * 每次审批动作写入一条不可变记录，形成审批时间线。
 */
@Getter
@Setter
@TableName("contract_approval")
public class ContractApproval extends BaseEntity {

    private String contractId;

    /** 审批动作 SUBMIT/APPROVE/REJECT/RESUBMIT/SIGN/ARCHIVE/CANCEL */
    private String action;

    private String fromStatus;

    private String toStatus;

    private String approverId;

    private String approverName;

    /** 审批意见 */
    private String opinion;
}
