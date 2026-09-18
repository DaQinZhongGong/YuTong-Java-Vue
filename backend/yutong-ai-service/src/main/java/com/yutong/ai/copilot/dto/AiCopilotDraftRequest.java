package com.yutong.ai.copilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiCopilotDraftRequest {

    @NotBlank(message = "prompt 不能为空")
    private String prompt;

    /** form / page / entity */
    private String targetType;

    private String entityCode;
}
