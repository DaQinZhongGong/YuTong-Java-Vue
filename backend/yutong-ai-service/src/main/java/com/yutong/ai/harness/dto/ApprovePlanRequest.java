package com.yutong.ai.harness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ApprovePlanRequest {

    @NotBlank
    private String planId;

    private long expectedRevision;

    @NotBlank
    @Size(max = 64)
    private String expectedHash;

    @Size(max = 128)
    private String idempotencyKey;
}
