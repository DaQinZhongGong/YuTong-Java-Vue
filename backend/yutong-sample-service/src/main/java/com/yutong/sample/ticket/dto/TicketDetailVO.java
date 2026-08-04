package com.yutong.sample.ticket.dto;

import com.yutong.sample.ticket.domain.WorkTicketLog;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 工单详情 VO（主表 + 处理记录时间线）。
 */
@Data
public class TicketDetailVO {
    private String id;
    private String ticketNo;
    private String title;
    private String description;
    private String categoryId;
    private String categoryNameSnapshot;
    private String priority;
    private String status;
    private String reporterId;
    private String reporterNameSnapshot;
    private String handlerId;
    private String handlerNameSnapshot;
    private OffsetDateTime slaDeadline;
    private OffsetDateTime assignedTime;
    private OffsetDateTime resolvedTime;
    private OffsetDateTime closedTime;
    private Integer satisfactionScore;
    private String satisfactionComment;
    private OffsetDateTime createdTime;
    private String createdBy;
    /** 处理记录时间线，按时间升序 */
    private List<WorkTicketLog> logs;
}
