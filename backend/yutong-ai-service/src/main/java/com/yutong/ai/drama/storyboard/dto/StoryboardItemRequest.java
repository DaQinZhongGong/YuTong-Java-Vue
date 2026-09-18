package com.yutong.ai.drama.storyboard.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单条分镜写入项 (创建项目内联 / 批量追加共用)。
 */
@Data
public class StoryboardItemRequest {

    /** 场次号, 项目内唯一且决定 compose 排序 */
    @NotNull(message = "sceneNo 不能为空")
    private Integer sceneNo;

    @Size(max = 32, message = "shotType 不超过 32 字")
    private String shotType;

    @Size(max = 128, message = "locationName 不超过 128 字")
    private String locationName;

    @Size(max = 4000, message = "imagePrompt 不超过 4000 字")
    private String imagePrompt;

    @NotBlank(message = "videoPrompt 不能为空")
    @Size(max = 4000, message = "videoPrompt 不超过 4000 字")
    private String videoPrompt;

    /** 时长秒 (0,60], 默认 5 */
    @DecimalMin(value = "0", inclusive = false, message = "durationSeconds 必须大于 0")
    @DecimalMax(value = "60", message = "durationSeconds 不超过 60")
    private BigDecimal durationSeconds = new BigDecimal("5");

    /** 参考图 URL 列表 (可空; ≥2 走多参考 image-to-video) */
    @Size(max = 8, message = "referenceImages 最多 8 张")
    private List<@Size(max = 1024, message = "参考图 URL 不超过 1024 字") String> referenceImages;
}
