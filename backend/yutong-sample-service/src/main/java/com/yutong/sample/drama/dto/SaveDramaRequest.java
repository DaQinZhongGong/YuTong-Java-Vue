package com.yutong.sample.drama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveDramaRequest {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String synopsis;
    private String status;
    private String remark;
}
