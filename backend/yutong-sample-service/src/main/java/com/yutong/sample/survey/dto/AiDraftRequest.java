package com.yutong.sample.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * AI 生成问卷题目草稿请求。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)。
 *
 * <p>调用 AI Gateway 生成问卷题目草稿, 返回符合规范的题目列表 JSON。
 * 用户可在前端"问卷设计器"中预览、编辑、采纳。
 *
 * <p>设计约束:
 * <ul>
 *   <li>AI 不直接写入数据库, 只返回草稿 JSON</li>
 *   <li>用户必须在前端预览并确认后, 通过 saveQuestion 接口写入</li>
 *   <li>调用 AI 服务时不在事务内</li>
 * </ul>
 */
@Data
public class AiDraftRequest {

    /** AI 生成提示词 (描述问卷主题/目标人群/题目数量等) */
    @NotBlank(message = "AI 草稿提示词不能为空")
    @Size(max = 1024, message = "AI 草稿提示词长度不能超过 1024")
    private String prompt;

    /** 期望生成的题目数量 (默认 5) */
    private Integer expectedCount;
}
