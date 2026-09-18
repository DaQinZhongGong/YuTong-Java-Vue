package com.yutong.ai.harness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class QueueInputRequest {

    /** STEER / FOLLOW_UP */
    @NotBlank
    private String type;

    @NotBlank
    @Size(max = 4000)
    private String content;
}
