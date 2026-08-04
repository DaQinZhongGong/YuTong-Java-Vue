package com.yutong.sample.ticket.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 工单处理记录。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 * 每次状态流转自动写入一条记录，形成处理时间线。
 */
@Getter
@Setter
@TableName("work_ticket_log")
public class WorkTicketLog extends BaseEntity {

    private String ticketId;

    /** 操作动作: CREATE/ASSIGN/ACCEPT/TRANSFER/SUSPEND/RESOLVE/REOPEN/CLOSE/RESUME/EVALUATE */
    private String action;

    private String fromStatus;

    private String toStatus;

    private String operatorId;

    private String operatorName;

    private String comment;
}
