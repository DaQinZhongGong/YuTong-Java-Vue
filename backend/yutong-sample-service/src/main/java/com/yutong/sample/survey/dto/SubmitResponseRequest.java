package com.yutong.sample.survey.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 提交答卷请求。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)。
 *
 * <p>前端收集所有题目的答案后, 一次性提交。
 * 后端会按 sur_question.question_code 校验答案完整性 + 字段校验规则。
 */
@Data
public class SubmitResponseRequest {

    /** 问卷 ID */
    @NotBlank(message = "问卷 ID 不能为空")
    private String surveyId;

    /** 来源渠道: WEB_ADMIN / MOBILE_UNIAPP / API */
    @Size(max = 32, message = "来源渠道长度不能超过 32")
    private String source;

    /** 答案列表 (每题一条) */
    @NotEmpty(message = "答案不能为空")
    @Valid
    private List<AnswerItem> answers;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;

    /**
     * 单题答案。
     */
    @Data
    public static class AnswerItem {
        /** 题目 ID */
        @NotBlank(message = "题目 ID 不能为空")
        private String questionId;

        /** 题目编号 (冗余, 便于校验) */
        @NotBlank(message = "题目编号不能为空")
        private String questionCode;

        /** 答案值 JSON 字符串 (与 sur_answer.answer_value 一致格式) */
        private String answerValue;

        /** 答案文本 (冗余, 用于统计快速展示) */
        @Size(max = 2048, message = "答案文本长度不能超过 2048")
        private String answerText;

        /** 选项 code 列表 JSON (SINGLE_CHOICE/MULTI_CHOICE) */
        private String selectedOptions;

        /** 评分 (RATING 题型冗余) */
        private Integer ratingScore;

        /** 作答耗时 (毫秒) */
        private Long durationMs;
    }
}
