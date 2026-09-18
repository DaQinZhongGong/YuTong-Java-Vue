package com.yutong.ai.drama.storyboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量保存分镜请求 (追加到既有项目)。
 */
@Data
public class StoryboardSaveRequest {

    @NotEmpty(message = "storyboards 不能为空")
    @Valid
    @Size(max = 50, message = "storyboards 最多 50 条")
    private List<StoryboardItemRequest> storyboards;
}
