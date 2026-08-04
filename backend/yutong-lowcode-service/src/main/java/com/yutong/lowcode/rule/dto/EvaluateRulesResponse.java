package com.yutong.lowcode.rule.dto;

import com.yutong.lowcode.rule.service.RuleEvalResult;

/**
 * 规则求值响应。设计来源: 36-低代码高级能力设计。
 *
 * @param result 规则求值结果 (按规则类型分组)
 */
public record EvaluateRulesResponse(RuleEvalResult result) {
}
