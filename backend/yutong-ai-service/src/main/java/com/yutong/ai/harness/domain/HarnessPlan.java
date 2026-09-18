package com.yutong.ai.harness.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.ai.harness.enums.PlanMode;
import com.yutong.ai.harness.enums.PlanReviewState;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Harness 任务计划。表: ai_harness_plan（V059）。
 */
@Getter
@Setter
@TableName("ai_harness_plan")
public class HarnessPlan extends BaseEntity {

    private String sessionId;
    private String runId;
    private String taskId;
    private String mode;
    private String reviewState;
    private String planMd;
    private String stepsJson;
    private String canonicalHash;
    private String feedback;
    private Long expectedRevision;
    private String idempotencyKey;
    private String approvedBy;
    private OffsetDateTime approvedAt;

    public PlanMode modeEnum() {
        return PlanMode.parse(mode);
    }

    public PlanReviewState reviewStateEnum() {
        return reviewState == null ? null : PlanReviewState.valueOf(reviewState);
    }
}
