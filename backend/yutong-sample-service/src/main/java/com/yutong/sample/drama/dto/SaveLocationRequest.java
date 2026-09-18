package com.yutong.sample.drama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveLocationRequest {
    @NotBlank(message = "场景地名称不能为空")
    private String name;
    private String description;
    private String imageUrl;
    private String remark;
}
