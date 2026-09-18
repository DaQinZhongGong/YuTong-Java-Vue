package com.yutong.ai.drama.storyboard.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 单镜视频生成请求。字段可空 — 空表示完全使用分镜既有 prompt/时长。
 */
@Data
public class VideoGenerateRequest {

    /** 覆盖视频提示词 (可空) */
    @Size(max = 4000, message = "videoPrompt 不超过 4000 字")
    private String videoPrompt;

    /** 指定供应商编码 (可空) */
    @Size(max = 64, message = "providerCode 不超过 64 字")
    private String providerCode;

    /** 指定模型编码 (可空) */
    @Size(max = 128, message = "modelCode 不超过 128 字")
    private String modelCode;
}
