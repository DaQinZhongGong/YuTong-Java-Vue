package com.yutong.lowcode.rule.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * 表达式语法校验请求。设计来源: 36-低代码高级能力设计。
 *
 * @param expression 待校验表达式
 * @param context    可选上下文 (语法校验不执行，仅做词法/语法解析)
 */
public record ValidateExpressionRequest(
        @NotBlank(message = "expression 不能为空") String expression,
        Map<String, Object> context
) {
}
