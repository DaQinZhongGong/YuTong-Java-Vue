package com.yutong.lowcode.rule.dto;

import com.yutong.lowcode.rule.model.Rule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

/**
 * 规则求值请求。设计来源: 36-低代码高级能力设计。
 *
 * @param rules   规则集合 (按 priority 排序后逐个求值)
 * @param context 求值上下文 (form/row/user/tenant/dict/params)
 */
public record EvaluateRulesRequest(
        @NotEmpty(message = "rules 不能为空") @Valid List<Rule> rules,
        Map<String, Object> context
) {
}
