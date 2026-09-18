package com.yutong.sample.drama.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveSceneRequest {
    @NotNull(message = "sceneNo 不能为空")
    @Min(value = 1, message = "sceneNo 必须 >=1")
    private Integer sceneNo;
    private String description;
    private String characterConsistencyJson;
    private String remark;
}
