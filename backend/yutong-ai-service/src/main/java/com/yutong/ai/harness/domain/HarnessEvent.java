package com.yutong.ai.harness.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Harness 事件账本（append-only）。表: ai_harness_event（V059）。
 * 无 BaseEntity：该表无 deleted/version/updated 列，主键 varchar(64)。
 */
@Getter
@Setter
@TableName("ai_harness_event")
public class HarnessEvent {

    @TableId(type = IdType.INPUT)
    private String id;

    private String tenantId;
    private String sessionId;
    private String runId;
    private Long sequenceNo;
    private String eventType;
    private String stepId;
    private String toolCallId;
    private String approvalId;
    private String payloadJson;
    private OffsetDateTime createdTime;
}
