package com.yutong.ai.harness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateRunRequest {

    @NotBlank
    @Size(max = 8000)
    private String requirement;

    /** 覆盖会话权限模式（可选） */
    private String permissionMode;

    private Integer maxToolCalls;
    private Long maxInputTokens;
    private Long maxOutputTokens;
    private Integer maxIterations;
    private Long maxWallTimeMs;

    @Size(max = 128)
    private String idempotencyKey;
}
