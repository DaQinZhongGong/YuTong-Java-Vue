package com.yutong.lowcode.rule.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 规则模型。设计来源: 36-低代码高级能力设计 (line 42-49)。
 *
 * <p>不可变 record，由设计器配置后传入 {@code RuleEvaluator} 求值。
 *
 * @param ruleId      规则唯一标识
 * @param ruleName    规则名称 (用于审计/消息)
 * @param ruleType    规则类型
 * @param expression  规则表达式 (经 {@code ExpressionEngine} 求值)
 * @param message     校验失败消息 (仅 VALIDATION 必填，其余可空)
 * @param targetField 目标字段 (计算/联动/默认值/只读/显隐规则的目标字段)
 * @param priority    优先级 (数值越小越先执行，null 视为 0)
 */
public record Rule(
        @NotBlank String ruleId,
        @NotBlank String ruleName,
        @NotNull RuleType ruleType,
        @NotBlank String expression,
        String message,
        String targetField,
        Integer priority
) {
}
