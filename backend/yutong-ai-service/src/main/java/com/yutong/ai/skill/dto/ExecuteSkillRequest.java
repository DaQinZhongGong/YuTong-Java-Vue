package com.yutong.ai.skill.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 执行内置/自定义 Skill。
 * action: analyze（解析输入文本/内容）或 create（生成 docx/pdf/xlsx 并落 MinIO）。
 */
@Data
public class ExecuteSkillRequest {

    /** analyze / create，默认 analyze */
    private String action;

    /** 待分析或写入文档的正文 */
    @NotBlank(message = "content 不能为空")
    private String content;

    /** 生成文件名（不含扩展名），create 时可选 */
    private String fileName;

    /** 文档标题，create 时可选 */
    private String title;
}
