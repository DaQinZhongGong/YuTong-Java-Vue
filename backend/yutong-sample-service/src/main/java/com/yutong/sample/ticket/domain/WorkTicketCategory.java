package com.yutong.sample.ticket.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 工单分类。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 * sla_hours 决定工单 SLA 截止时间。
 */
@Getter
@Setter
@TableName("work_ticket_category")
public class WorkTicketCategory extends BaseEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    private String categoryCode;

    private String categoryName;

    /** SLA 时长（小时），用于计算工单截止时间 */
    private Integer slaHours;

    private String status;
}
