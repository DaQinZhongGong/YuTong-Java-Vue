package com.yutong.ai.harness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateSessionRequest {

    @NotBlank
    @Size(max = 512)
    private String workspacePath;

    @Size(max = 200)
    private String title;

    @Size(max = 128)
    private String model;

    /** READ_ONLY / WORKSPACE_WRITE / FULL_ACCESS */
    private String permissionMode;

    /** ON_REQUEST / NEVER */
    private String approvalPolicy;

    /** NONE / LOW / MEDIUM / HIGH */
    private String thinkingLevel;

    private String verificationMode;

    /** 创建幂等键 */
    @Size(max = 128)
    private String idempotencyKey;
}
