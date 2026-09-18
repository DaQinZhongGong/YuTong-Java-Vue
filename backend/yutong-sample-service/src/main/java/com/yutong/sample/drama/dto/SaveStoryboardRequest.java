package com.yutong.sample.drama.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveStoryboardRequest {
    @NotNull(message = "storyboardNo 不能为空")
    @Min(value = 1, message = "storyboardNo 必须 >=1")
    private Integer storyboardNo;
    private String sceneId;
    private String prompt;
    private String imageUrl;
    private String audioId;
    private String status;
    private String remark;
}
