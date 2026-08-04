package com.yutong.ai.governance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交 AI 用户反馈请求。设计来源: 37-AI治理与评测设计 人工反馈闭环章节
 * <p>6 类反馈类型: HELPFUL / NOT_HELPFUL / INACCURATE / CITATION_ERROR / FORMAT_ERROR / RISKY
 */
@Data
public class SubmitFeedbackRequest {

    /** HELPFUL / NOT_HELPFUL / INACCURATE / CITATION_ERROR / FORMAT_ERROR / RISKY */
    @NotBlank(message = "反馈类型不能为空")
    private String feedbackType;

    /** ANSWER / TOOL / GENERATION */
    private String targetType = "ANSWER";

    private String targetId;
    private String conversationId;
    private String messageId;
    private String scenario;
    private String modelCode;

    /** 1~5 星评分, 可空 */
    private Integer rating;

    /** 反馈标签 JSON 数组字符串, 如 ["business-rule", "audit"] */
    private String tagsJson;

    @Size(max = 2000, message = "评论长度不能超过 2000")
    private String commentText;
}
