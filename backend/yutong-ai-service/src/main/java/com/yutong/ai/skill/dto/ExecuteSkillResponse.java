package com.yutong.ai.skill.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ExecuteSkillResponse {
    private String runId;
    private String skillCode;
    private String skillType;
    private String action;
    private String status;
    private String summary;
    private String outputFileId;
    private String downloadUrl;
    private Integer latencyMs;
}
