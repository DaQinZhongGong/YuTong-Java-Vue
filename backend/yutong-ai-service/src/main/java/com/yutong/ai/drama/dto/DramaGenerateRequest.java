package com.yutong.ai.drama.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DramaGenerateRequest {

    @NotBlank(message = "title 不能为空")
    private String title;

    private String synopsis;

    /** 角色一致性描述，例如「男主黑发短寸、灰色风衣」 */
    private String characterLock;

    /** 画幅比例: 9:16(竖屏短视频) / 16:9(横屏) / 1:1(方形), 默认 9:16 */
    @Pattern(regexp = "^(9:16|16:9|1:1)$", message = "画幅仅支持 9:16 / 16:9 / 1:1")
    private String aspectRatio;

    /** 画风: realistic(写实) / anime(动漫) / cartoon(卡通) / cinematic(电影感), 默认 realistic */
    @Pattern(regexp = "^(realistic|anime|cartoon|cinematic)$",
            message = "画风仅支持 realistic/anime/cartoon/cinematic")
    private String style;

    /** 转场类型: fade(淡入淡出) / cut(硬切) / dissolve(溶解), 默认 fade */
    @Pattern(regexp = "^(fade|cut|dissolve)$", message = "转场仅支持 fade/cut/dissolve")
    private String transition;

    /** 获取画幅 (带默认值) */
    public String getAspectRatioOrDefault() {
        return (aspectRatio == null || aspectRatio.isBlank()) ? "9:16" : aspectRatio;
    }

    /** 获取画风 (带默认值) */
    public String getStyleOrDefault() {
        return (style == null || style.isBlank()) ? "realistic" : style;
    }

    /** 获取转场 (带默认值) */
    public String getTransitionOrDefault() {
        return (transition == null || transition.isBlank()) ? "fade" : transition;
    }
}
