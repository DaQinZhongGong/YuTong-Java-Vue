package com.yutong.sample.drama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveAudioRequest {
    @NotBlank(message = "音频名称不能为空")
    private String name;
    private String audioUrl;
    private Integer durationSeconds;
    private String voiceId;
    private String remark;
}
