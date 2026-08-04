package com.yutong.sample.request.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 申请单主表。设计来源: 18-样例业务详细设计、57-完整DDL清单 biz_request
 * 状态机: DRAFT→SUBMITTED→APPROVED/REJECTED，REJECTED→DRAFT(edit)，APPROVED→ARCHIVED
 */
@Getter
@Setter
@TableName("biz_request")
public class BizRequest extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 单号，规则 REQyyyyMMddNNNN */
    private String requestNo;

    private String title;

    private String customerId;

    /** 客户名称快照，防客户更名后展示错误 */
    private String customerNameSnapshot;

    private String applyReason;

    /** 状态枚举 DRAFT/SUBMITTED/APPROVED/REJECTED/ARCHIVED */
    private String requestStatus;

    /** 合计金额，由后端按明细重新计算 */
    private BigDecimal totalAmount;

    /** 申请人 ID，数据权限 SELF 字段 */
    private String applicantId;

    private String applicantNameSnapshot;

    /** 数据权限归属用户，默认等于 applicantId */
    private String ownerUserId;

    private String ownerDeptId;

    private String ownerDeptPath;

    private OffsetDateTime submittedTime;

    private OffsetDateTime approvedTime;

    private OffsetDateTime archivedTime;
}
