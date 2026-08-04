package com.yutong.sample.ticket.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 工单主表。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 * 状态机: NEW→ASSIGNED→PROCESSING→COMPLETED→CLOSED，含 SUSPENDED 挂起分支。
 * 验证能力: SLA 定时扫描、派单/转单、评价、消息通知。
 */
@Getter
@Setter
@TableName("work_ticket")
public class WorkTicket extends BaseEntity {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SUSPENDED = "SUSPENDED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CLOSED = "CLOSED";

    public static final String PRIORITY_LOW = "LOW";
    public static final String PRIORITY_MEDIUM = "MEDIUM";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";

    /** 工单号，规则 WTyyyyMMddNNNN */
    private String ticketNo;

    private String title;

    private String description;

    private String categoryId;

    /** 分类名称快照，防分类更名后展示错误 */
    private String categoryNameSnapshot;

    /** 优先级 LOW/MEDIUM/HIGH/URGENT */
    private String priority;

    /** 状态 NEW/ASSIGNED/PROCESSING/SUSPENDED/COMPLETED/CLOSED */
    private String status;

    private String reporterId;

    private String reporterNameSnapshot;

    private String handlerId;

    private String handlerNameSnapshot;

    /** 数据权限归属用户，默认等于 reporterId 或 createdBy */
    private String ownerUserId;

    private String ownerDeptId;

    private String ownerDeptPath;

    /** SLA 截止时间，由分类 sla_hours 计算 */
    private OffsetDateTime slaDeadline;

    private OffsetDateTime assignedTime;

    private OffsetDateTime resolvedTime;

    private OffsetDateTime closedTime;

    /** 评价分数 1-5 */
    private Integer satisfactionScore;

    private String satisfactionComment;
}
