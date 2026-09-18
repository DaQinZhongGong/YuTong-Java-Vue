package com.yutong.ai.drama.storyboard.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建短剧项目请求。可内联携带分镜列表, 也可后续 saveStoryboards 补齐。
 */
@Data
public class CreateProjectRequest {

    @NotBlank(message = "title 不能为空")
    @Size(max = 256, message = "title 不超过 256 字")
    private String title;

    @Size(max = 4000, message = "synopsis 不超过 4000 字")
    private String synopsis;

    @Size(max = 64, message = "artStyle 不超过 64 字")
    private String artStyle;

    @Size(max = 1024, message = "styleRef 不超过 1024 字")
    private String styleRef;

    /** 画幅: 16:9 / 9:16 / 1:1 / 4:3, 默认 16:9 */
    @Pattern(regexp = "^(16:9|9:16|1:1|4:3)$", message = "aspectRatio 仅支持 16:9/9:16/1:1/4:3")
    private String aspectRatio = "16:9";

    /** 扩展元数据 JSON (可空) */
    @Size(max = 8000, message = "metaJson 不超过 8000 字")
    private String metaJson;

    /** 内联分镜 (可空, 最多 50) */
    @Valid
    @Size(max = 50, message = "storyboards 最多 50 条")
    private List<StoryboardItemRequest> storyboards;

    public String getAspectRatioOrDefault() {
        return (aspectRatio == null || aspectRatio.isBlank()) ? "16:9" : aspectRatio;
    }
}
