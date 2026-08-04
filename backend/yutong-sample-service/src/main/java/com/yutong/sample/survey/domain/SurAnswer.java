package com.yutong.sample.survey.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 答题表。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单。
 *
 * <p>GA2-42 落地: 一份答卷下每道题的答案, 一对一关系 (response_id + question_id 唯一)。
 *
 * <p>answer_value 格式 (JSON):
 * <ul>
 *   <li>SINGLE_CHOICE: "OPT_A"</li>
 *   <li>MULTI_CHOICE: ["OPT_A","OPT_B"]</li>
 *   <li>TEXT/TEXTAREA: "用户输入文本"</li>
 *   <li>RATING: 5</li>
 *   <li>DATE: "2026-07-19"</li>
 *   <li>MATRIX: {"R1":"C1","R2":"C2"}</li>
 * </ul>
 *
 * <p>selected_options: 选项 code 列表 (冗余, 用于统计聚合)
 * <p>rating_score: 评分 (RATING 题型冗余, 便于统计)
 */
@Getter
@Setter
@TableName("sur_answer")
public class SurAnswer extends BaseEntity {
    /** 所属答卷 ID */
    private String responseId;
    /** 所属问卷 ID (冗余, 便于统计查询) */
    private String surveyId;
    /** 题目 ID */
    private String questionId;
    /** 题目编号 (冗余) */
    private String questionCode;
    /** 题目类型 (冗余) */
    private String questionType;
    /** 答案值 JSON */
    private String answerValue;
    /** 答案文本 (冗余, 用于统计快速展示) */
    private String answerText;
    /** 选项 code 列表 (冗余, 用于统计聚合, MULTI_CHOICE 存数组) */
    private String selectedOptions;
    /** 评分 (RATING 题型冗余, 便于统计) */
    private Integer ratingScore;
    /** 作答耗时 (毫秒, 单题作答时长) */
    private Long durationMs;
}
