package com.yutong.ai.copilot.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("ai_copilot_run")
public class AiCopilotRun extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_FAILED = "FAILED";

    private String targetType;
    private String prompt;
    private String status;
    private String providerCode;
    private String modelCode;

    @TableField("output_json")
    private String outputJson;

    @TableField("plan_json")
    private String planJson;

    @TableField("outbox_json")
    private String outboxJson;

    private Integer loopCount;
}
