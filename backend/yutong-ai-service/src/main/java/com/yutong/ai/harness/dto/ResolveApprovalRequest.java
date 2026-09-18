package com.yutong.ai.harness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResolveApprovalRequest {

    /** APPROVE / DENY */
    @NotBlank
    private String decision;

    private long expectedRevision;

    @NotBlank
    @Size(max = 64)
    private String argumentsSha256;

    @Size(max = 64)
    private String decisionId;

    @Size(max = 500)
    private String note;
}
