package com.yutong.sample.survey.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新题目请求。设计来源: 35-样例业务矩阵扩展设计 P2 问卷表单 (GA2-42)。
 *
 * <p>题目类型 questionType:
 * <ul>
 *   <li>SINGLE_CHOICE: 单选 (optionsJson 提供选项)</li>
 *   <li>MULTI_CHOICE: 多选</li>
 *   <li>TEXT: 单行文本</li>
 *   <li>TEXTAREA: 多行文本</li>
 *   <li>RATING: 评分</li>
 *   <li>DATE: 日期</li>
 *   <li>MATRIX: 矩阵题</li>
 * </ul>
 */
@Data
public class SaveQuestionRequest {

    /** 题目编号 (问卷内唯一, Q001/Q002/...) */
    @NotBlank(message = "题目编号不能为空")
    @Size(max = 32, message = "题目编号长度不能超过 32")
    private String questionCode;

    /** 题目类型 */
    @NotBlank(message = "题目类型不能为空")
    @Size(max = 32, message = "题目类型长度不能超过 32")
    private String questionType;

    /** 题目标题 */
    @NotBlank(message = "题目标题不能为空")
    @Size(max = 512, message = "题目标题长度不能超过 512")
    private String title;

    /** 题目描述 */
    @Size(max = 1024, message = "题目描述长度不能超过 1024")
    private String description;

    /** 是否必答 */
    private Boolean required;

    /** 排序号 */
    private Integer sortNo;

    /** 选项 JSON */
    private String optionsJson;

    /** 校验规则 JSON */
    private String validationJson;

    /** 条件显隐规则 JSON */
    private String logicJson;

    /** 矩阵题行/列配置 JSON */
    private String matrixJson;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
