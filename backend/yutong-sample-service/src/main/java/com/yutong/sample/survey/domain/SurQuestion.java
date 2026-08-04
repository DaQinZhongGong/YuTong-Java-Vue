package com.yutong.sample.survey.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 题目表。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单。
 *
 * <p>GA2-42 落地: 题目元信息 + 选项 + 校验规则 + 条件显隐规则。
 *
 * <p>题目类型:
 * <ul>
 *   <li>SINGLE_CHOICE: 单选 (options_json 提供选项)</li>
 *   <li>MULTI_CHOICE: 多选 (options_json 提供选项, validation_json 可指定 minSelect/maxSelect)</li>
 *   <li>TEXT: 单行文本 (validation_json 可指定 minLength/maxLength/regex)</li>
 *   <li>TEXTAREA: 多行文本</li>
 *   <li>RATING: 评分 (options_json 提供 1-5 分值)</li>
 *   <li>DATE: 日期</li>
 *   <li>MATRIX: 矩阵题 (matrix_json 提供行/列)</li>
 * </ul>
 *
 * <p>条件显隐 (logic_json): 数组, 满足任一条件即触发 action。
 * 格式: [{"questionCode":"Q001","operator":"EQ","value":"A","action":"SHOW"}]
 * operator: EQ / NE / IN / NOT_IN / CONTAINS / GT / GTE / LT / LTE
 * action: SHOW / HIDE / REQUIRE
 */
@Getter
@Setter
@TableName("sur_question")
public class SurQuestion extends BaseEntity {
    /** 所属问卷 ID */
    private String surveyId;
    /** 题目编号 (问卷内唯一, Q001/Q002/...) */
    private String questionCode;
    /** 题目类型: SINGLE_CHOICE / MULTI_CHOICE / TEXT / TEXTAREA / RATING / DATE / MATRIX */
    private String questionType;
    /** 题目标题 */
    private String title;
    /** 题目描述 (帮助文字) */
    private String description;
    /** 是否必答 */
    private Boolean required;
    /** 排序号 (问卷内题目顺序) */
    private Integer sortNo;
    /** 选项 JSON (SINGLE_CHOICE/MULTI_CHOICE/RATING/MATRIX 类型使用) */
    private String optionsJson;
    /** 校验规则 JSON */
    private String validationJson;
    /** 条件显隐规则 JSON (数组, 满足任一条件即触发 action) */
    private String logicJson;
    /** 矩阵题行/列配置 (MATRIX 类型使用) */
    private String matrixJson;
    /** AI 生成标记 (true 表示由 AI 生成草稿) */
    private Boolean aiGenerated;

    /** question_type 常量 */
    public static final String TYPE_SINGLE_CHOICE = "SINGLE_CHOICE";
    public static final String TYPE_MULTI_CHOICE = "MULTI_CHOICE";
    public static final String TYPE_TEXT = "TEXT";
    public static final String TYPE_TEXTAREA = "TEXTAREA";
    public static final String TYPE_RATING = "RATING";
    public static final String TYPE_DATE = "DATE";
    public static final String TYPE_MATRIX = "MATRIX";

    /** logic action 常量 */
    public static final String LOGIC_ACTION_SHOW = "SHOW";
    public static final String LOGIC_ACTION_HIDE = "HIDE";
    public static final String LOGIC_ACTION_REQUIRE = "REQUIRE";

    /** logic operator 常量 */
    public static final String LOGIC_OP_EQ = "EQ";
    public static final String LOGIC_OP_NE = "NE";
    public static final String LOGIC_OP_IN = "IN";
    public static final String LOGIC_OP_NOT_IN = "NOT_IN";
    public static final String LOGIC_OP_CONTAINS = "CONTAINS";
    public static final String LOGIC_OP_GT = "GT";
    public static final String LOGIC_OP_GTE = "GTE";
    public static final String LOGIC_OP_LT = "LT";
    public static final String LOGIC_OP_LTE = "LTE";
}
