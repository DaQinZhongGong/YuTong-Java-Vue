package com.yutong.sample.request.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 审批记录。设计来源: 18-样例业务详细设计、57-完整DDL清单 biz_approval_record
 * action: SUBMIT/APPROVE/REJECT/WITHDRAW/ARCHIVE
 * result: APPROVED/REJECTED (仅 APPROVE/REJECT 填写)
 */
@Getter
@Setter
@TableName("biz_approval_record")
public class ApprovalRecord extends BaseEntity {

    public static final String ACTION_SUBMIT = "SUBMIT";
    public static final String ACTION_APPROVE = "APPROVE";
    public static final String ACTION_REJECT = "REJECT";
    public static final String ACTION_WITHDRAW = "WITHDRAW";
    public static final String ACTION_ARCHIVE = "ARCHIVE";

    public static final String RESULT_APPROVED = "APPROVED";
    public static final String RESULT_REJECTED = "REJECTED";

    private String requestId;

    /** 动作: SUBMIT/APPROVE/REJECT/WITHDRAW/ARCHIVE */
    private String action;

    /** 结果: APPROVED/REJECTED */
    private String result;

    private String opinion;

    private String operatorId;

    private OffsetDateTime operatedTime;
}
