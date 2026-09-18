package com.yutong.sample.drama.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SaveCharacterRequest {
    @NotBlank(message = "角色名称不能为空")
    private String name;
    private String role;
    private String description;
    private String remark;
}
