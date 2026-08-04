package com.yutong.lowcode.rule.service;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.lowcode.rule.engine.ExpressionEngine;
import com.yutong.lowcode.rule.model.Rule;
import com.yutong.lowcode.rule.model.RuleType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则求值器。设计来源: 36-低代码高级能力设计 (line 42-49)。
 *
 * <p>按 {@link Rule#priority()} 升序排序后逐个求值，根据 {@link RuleType} 分流处理，
 * 结果聚合到 {@link RuleEvalResult}。
 *
 * <p>各类规则语义:
 * <ul>
 *   <li>VALIDATION: 表达式求值 false 时返回错误消息</li>
 *   <li>VISIBILITY: 求值结果决定 targetField 是否可见</li>
 *   <li>LINKAGE: 求值结果为 Map 时合并到上下文 (联动)；非 Map 时赋给 targetField</li>
 *   <li>COMPUTATION: 求值结果赋给 targetField，并回写上下文供后续规则使用</li>
 *   <li>DEFAULT_VALUE: targetField 为空时求值赋值</li>
 *   <li>READONLY: 求值结果决定 targetField 是否只读</li>
 * </ul>
 *
 * <p>健壮性: 单条规则求值异常不会中断整批；VALIDATION 异常记为校验失败消息，其余类型跳过。
 * 上下文做浅拷贝且单独拷贝 form Map，避免回写污染调用方数据。
 */
@Component
public class RuleEvaluator {

    private final ExpressionEngine engine;

    public RuleEvaluator(ExpressionEngine engine) {
        this.engine = engine;
    }

    /**
     * 求值规则集合。
     *
     * @param rules   规则集合 (null/empty 返回空结果)
     * @param context 求值上下文 (form/row/user/tenant/dict/params)，可为 null
     * @return 按规则类型分组的求值结果
     */
    public RuleEvalResult evaluate(List<Rule> rules, Map<String, Object> context) {
        RuleEvalResult result = RuleEvalResult.empty();
        if (rules == null || rules.isEmpty()) {
            return result;
        }
        Map<String, Object> ctx = copyContext(context);
        ensureFormMap(ctx);

        List<Rule> sorted = new ArrayList<>(rules);
        sorted.sort(Comparator.comparingInt(r -> r.priority() == null ? 0 : r.priority()));

        for (Rule rule : sorted) {
            try {
                applyRule(rule, ctx, result);
            } catch (Exception e) {
                if (rule.ruleType() == RuleType.VALIDATION) {
                    String msg = rule.message() != null ? rule.message() : ("规则求值异常: " + safeMsg(e));
                    result.validations().add(msg);
                }
                // 其余类型: 单条异常跳过，不中断整批
            }
        }
        return result;
    }

    private void applyRule(Rule rule, Map<String, Object> ctx, RuleEvalResult result) {
        String expr = rule.expression();
        String target = rule.targetField();
        switch (rule.ruleType()) {
            case VALIDATION -> {
                if (!engine.evaluateCondition(expr, ctx)) {
                    result.validations().add(
                            rule.message() != null ? rule.message() : ("校验失败: " + rule.ruleName()));
                }
            }
            case VISIBILITY -> {
                requireTarget(rule, target);
                result.visibility().put(target, engine.evaluateCondition(expr, ctx));
            }
            case READONLY -> {
                requireTarget(rule, target);
                result.readonly().put(target, engine.evaluateCondition(expr, ctx));
            }
            case COMPUTATION -> {
                requireTarget(rule, target);
                Object val = engine.evaluate(expr, ctx);
                result.computations().put(target, val);
                setFormField(ctx, target, val);
            }
            case DEFAULT_VALUE -> {
                requireTarget(rule, target);
                if (isEmptyValue(getFormField(ctx, target))) {
                    Object val = engine.evaluate(expr, ctx);
                    result.defaults().put(target, val);
                    setFormField(ctx, target, val);
                }
            }
            case LINKAGE -> {
                Object val = engine.evaluate(expr, ctx);
                if (val instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> e : m.entrySet()) {
                        String k = String.valueOf(e.getKey());
                        Object v = e.getValue();
                        result.linkage().put(k, v);
                        setFormField(ctx, k, v);
                    }
                } else if (target != null) {
                    result.linkage().put(target, val);
                    setFormField(ctx, target, val);
                }
            }
        }
    }

    private void requireTarget(Rule rule, String target) {
        if (target == null || target.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    rule.ruleType() + " 规则缺少 targetField: " + rule.ruleId());
        }
    }

    @SuppressWarnings("unchecked")
    private void setFormField(Map<String, Object> ctx, String field, Object value) {
        Object form = ctx.get("form");
        if (form instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).put(field, value);
        } else {
            ctx.put(field, value);
        }
    }

    private Object getFormField(Map<String, Object> ctx, String field) {
        Object form = ctx.get("form");
        if (form instanceof Map<?, ?> m) {
            return m.get(field);
        }
        return ctx.get(field);
    }

    private void ensureFormMap(Map<String, Object> ctx) {
        Object form = ctx.get("form");
        if (form instanceof Map<?, ?> m) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                copy.put(String.valueOf(e.getKey()), e.getValue());
            }
            ctx.put("form", copy);
        } else {
            ctx.put("form", new LinkedHashMap<>());
        }
    }

    private Map<String, Object> copyContext(Map<String, Object> context) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (context != null) {
            ctx.putAll(context);
        }
        return ctx;
    }

    private static boolean isEmptyValue(Object v) {
        if (v == null) {
            return true;
        }
        if (v instanceof CharSequence c) {
            return c.isEmpty();
        }
        if (v instanceof Collection<?> c) {
            return c.isEmpty();
        }
        if (v instanceof Map<?, ?> m) {
            return m.isEmpty();
        }
        return false;
    }

    private static String safeMsg(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
