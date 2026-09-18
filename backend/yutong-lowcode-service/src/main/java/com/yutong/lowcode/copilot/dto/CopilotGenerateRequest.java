package com.yutong.lowcode.copilot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Copilot 生成请求。
 * 设计来源: Phase 7 编程助手占位 — prompt → form/page 模板生成
 */
@Data
public class CopilotGenerateRequest {

    @NotBlank(message = "prompt 不能为空")
    private String prompt;

    /** 目标类型: form / page / entity, 默认 form */
    private String targetType;

    /** 实体编码，可空 (有则关联到 LcEntity) */
    private String entityCode;

    /** 额外上下文 JSON，可空 */
    private String contextJson;
}
