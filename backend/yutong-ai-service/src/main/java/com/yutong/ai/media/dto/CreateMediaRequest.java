package com.yutong.ai.media.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建媒体任务请求。
 */
@Data
public class CreateMediaRequest {

    @NotBlank(message = "prompt 不能为空")
    private String prompt;

    /** 提供方编码，默认 mock */
    private String providerCode;

    /** 模型编码，可空 */
    private String modelCode;

    /** 扩展输入 JSON 字符串，可空 (透传至 input_json) */
    private String inputJson;

    /** 备注，可空 */
    private String remark;
}
