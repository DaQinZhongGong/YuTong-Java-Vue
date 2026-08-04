package com.yutong.lowcode.rule.dto;

/**
 * 表达式语法校验响应。设计来源: 36-低代码高级能力设计。
 *
 * @param valid 是否合法
 * @param error 不合法时的错误描述 (合法时为 null)
 */
public record ValidateExpressionResponse(boolean valid, String error) {
}
