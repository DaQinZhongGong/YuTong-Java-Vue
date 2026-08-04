package com.yutong.lowcode.rule.service;

import com.yutong.lowcode.rule.engine.ExpressionEngine;
import com.yutong.lowcode.rule.engine.WhiteListFunctions;
import com.yutong.lowcode.rule.model.Rule;
import com.yutong.lowcode.rule.model.RuleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则求值器单元测试。设计来源: 36-低代码高级能力设计 (line 42-49)。
 *
 * <p>覆盖 6 类规则 (VALIDATION/VISIBILITY/LINKAGE/COMPUTATION/DEFAULT_VALUE/READONLY)
 * 与 priority 排序、上下文回写、健壮性。
 */
@DisplayName("规则求值器")
class RuleEvaluatorTest {

    private final RuleEvaluator evaluator = new RuleEvaluator(
            new ExpressionEngine(new WhiteListFunctions()));

    private Rule rule(String id, RuleType type, String expr, String target, Integer priority, String message) {
        return new Rule(id, "rule-" + id, type, expr, message, target, priority);
    }

    private Map<String, Object> form(Map<String, Object> formData) {
        return Map.of("form", formData);
    }

    @Nested
    @DisplayName("VALIDATION 校验规则")
    class ValidationRules {

        @Test
        @DisplayName("表达式 false 返回错误消息")
        void validationFails() {
            Rule r = rule("v1", RuleType.VALIDATION, "${form.amount} > 100", null, 1, "金额必须大于100");
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of("amount", 50)));
            assertEquals(1, result.validations().size());
            assertEquals("金额必须大于100", result.validations().get(0));
        }

        @Test
        @DisplayName("表达式 true 不产生消息")
        void validationPasses() {
            Rule r = rule("v1", RuleType.VALIDATION, "${form.amount} > 100", null, 1, "金额必须大于100");
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of("amount", 200)));
            assertTrue(result.validations().isEmpty());
        }

        @Test
        @DisplayName("表达式异常记为校验失败 (不中断)")
        void validationExceptionRecorded() {
            Rule r = rule("v1", RuleType.VALIDATION, "unknownFn(1)", null, 1, "兜底消息");
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of()));
            assertEquals(1, result.validations().size());
            assertEquals("兜底消息", result.validations().get(0));
        }
    }

    @Nested
    @DisplayName("VISIBILITY / READONLY")
    class VisibilityReadonly {

        @Test
        @DisplayName("VISIBILITY 结果决定 targetField 可见性")
        void visibility() {
            Rule r = rule("vis1", RuleType.VISIBILITY, "${form.type} == 'VIP'", "vipPanel", 1, null);
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of("type", "VIP")));
            assertEquals(Boolean.TRUE, result.visibility().get("vipPanel"));

            RuleEvalResult result2 = evaluator.evaluate(List.of(r), form(Map.of("type", "NORMAL")));
            assertEquals(Boolean.FALSE, result2.visibility().get("vipPanel"));
        }

        @Test
        @DisplayName("READONLY 结果决定 targetField 只读状态")
        void readonly() {
            Rule r = rule("ro1", RuleType.READONLY, "${form.locked} == true", "amount", 1, null);
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of("locked", true)));
            assertEquals(Boolean.TRUE, result.readonly().get("amount"));
        }

        @Test
        @DisplayName("缺少 targetField 抛异常并被跳过 (不写入结果)")
        void missingTargetSkipped() {
            Rule r = rule("ro2", RuleType.READONLY, "true", null, 1, null);
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of()));
            // READONLY 异常被吞掉，不写入 readonly，不中断整批
            assertTrue(result.readonly().isEmpty());
        }
    }

    @Nested
    @DisplayName("COMPUTATION 计算规则")
    class ComputationRules {

        @Test
        @DisplayName("计算结果赋给 targetField")
        void computation() {
            Rule r = rule("c1", RuleType.COMPUTATION, "round(${form.price}, 2)", "tax", 1, null);
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of("price", 99.999)));
            assertEquals(100.0, ((Number) result.computations().get("tax")).doubleValue(), 0.0001);
        }

        @Test
        @DisplayName("计算结果回写上下文，供后续规则使用 (priority 排序)")
        void computationFeedsLaterRule() {
            Rule compute = rule("c1", RuleType.COMPUTATION, "round(${form.price}, 0)", "total", 1, null);
            Rule validate = rule("v1", RuleType.VALIDATION, "${form.total} > 50", null, 2, "总额过小");
            RuleEvalResult result = evaluator.evaluate(List.of(compute, validate), form(Map.of("price", 99.9)));
            // total 被计算为 100 并回写，后续校验 100 > 50 通过
            assertEquals(100.0, ((Number) result.computations().get("total")).doubleValue(), 0.0001);
            assertTrue(result.validations().isEmpty());
        }
    }

    @Nested
    @DisplayName("DEFAULT_VALUE 默认值规则")
    class DefaultValueRules {

        @Test
        @DisplayName("字段为空时填充默认值")
        void defaultFilledWhenEmpty() {
            Rule r = rule("d1", RuleType.DEFAULT_VALUE, "'DRAFT'", "status", 1, null);
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of()));
            assertEquals("DRAFT", result.defaults().get("status"));
        }

        @Test
        @DisplayName("字段非空时不覆盖")
        void defaultNotOverwritten() {
            Rule r = rule("d1", RuleType.DEFAULT_VALUE, "'DRAFT'", "status", 1, null);
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of("status", "APPROVED")));
            assertTrue(result.defaults().isEmpty());
        }
    }

    @Nested
    @DisplayName("LINKAGE 联动规则")
    class LinkageRules {

        @Test
        @DisplayName("求值结果为 Map 时合并到 linkage")
        void linkageMapMerged() {
            Map<String, Object> ctx = Map.of(
                    "form", Map.of(),
                    "dict", Map.of("overrides", Map.of("city", "BJ", "level", "VIP")));
            Rule r = rule("l1", RuleType.LINKAGE, "dict('overrides')", null, 1, null);
            RuleEvalResult result = evaluator.evaluate(List.of(r), ctx);
            assertEquals("BJ", result.linkage().get("city"));
            assertEquals("VIP", result.linkage().get("level"));
        }

        @Test
        @DisplayName("求值结果非 Map 时赋给 targetField")
        void linkageScalarToTarget() {
            Rule r = rule("l2", RuleType.LINKAGE, "upper(${form.name})", "label", 1, null);
            RuleEvalResult result = evaluator.evaluate(List.of(r), form(Map.of("name", "abc")));
            assertEquals("ABC", result.linkage().get("label"));
        }
    }

    @Nested
    @DisplayName("排序与健壮性")
    class OrderingAndRobustness {

        @Test
        @DisplayName("空规则集合返回空结果")
        void emptyRules() {
            RuleEvalResult result = evaluator.evaluate(List.of(), form(Map.of()));
            assertTrue(result.validations().isEmpty());
            assertTrue(result.computations().isEmpty());
        }

        @Test
        @DisplayName("null 上下文不抛异常")
        void nullContext() {
            Rule r = rule("d1", RuleType.DEFAULT_VALUE, "'X'", "field", 1, null);
            RuleEvalResult result = evaluator.evaluate(List.of(r), null);
            assertEquals("X", result.defaults().get("field"));
        }

        @Test
        @DisplayName("priority 数值越小越先执行")
        void priorityOrdering() {
            // priority 2 的计算先于 priority 1 的校验? 不——数值小先执行
            // 这里让计算 priority=1 先执行写入，校验 priority=2 后执行读取
            Rule compute = rule("c", RuleType.COMPUTATION, "10", "base", 1, null);
            Rule validate = rule("v", RuleType.VALIDATION, "${form.base} == 10", null, 2, "base 不对");
            RuleEvalResult result = evaluator.evaluate(List.of(compute, validate), form(Map.of()));
            assertTrue(result.validations().isEmpty());
            assertEquals(10, ((Number) result.computations().get("base")).intValue());
        }
    }
}
