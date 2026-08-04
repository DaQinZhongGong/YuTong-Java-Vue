package com.yutong.lowcode.rule.engine;

import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 表达式求值引擎单元测试。设计来源: 36-低代码高级能力设计。
 *
 * <p>覆盖: 变量引用 / 字面量 / 运算符 / 白名单函数 / 嵌套 / 超时 / 长度 / 危险关键字 / 语法校验。
 */
@DisplayName("规则表达式求值引擎")
class ExpressionEngineTest {

    private final WhiteListFunctions functions = new WhiteListFunctions();
    private final ExpressionEngine engine = new ExpressionEngine(functions);

    /**
     * 预热: 触发类加载 / JIT / 虚拟线程池初始化，
     * 避免首个测试因冷启动开销超过 50ms 默认超时阈值而产生假阳性。
     */
    @BeforeAll
    static void warmUp() {
        WhiteListFunctions f = new WhiteListFunctions();
        ExpressionEngine e = new ExpressionEngine(f);
        e.setTimeoutMs(5000);
        e.evaluate("1 > 0", Map.of());
        e.shutdown();
    }

    private static double d(Object o) {
        return ((Number) o).doubleValue();
    }

    private static int i(Object o) {
        return ((Number) o).intValue();
    }

    @Nested
    @DisplayName("变量引用与字面量")
    class VariablesAndLiterals {

        @Test
        @DisplayName("${form.amount} 点号路径解析")
        void dottedVarPath() {
            Map<String, Object> ctx = Map.of("form", Map.of("amount", 200));
            assertTrue(engine.evaluateCondition("${form.amount} > 100", ctx));
        }

        @Test
        @DisplayName("${user.role} 字符串比较")
        void stringVarCompare() {
            Map<String, Object> ctx = Map.of("user", Map.of("role", "admin"));
            assertTrue(engine.evaluateCondition("${user.role} == 'admin'", ctx));
        }

        @Test
        @DisplayName("缺失变量返回 null，null == null 为 true")
        void missingVarIsNull() {
            Map<String, Object> ctx = Map.of("form", Map.of());
            assertTrue(engine.evaluateCondition("${form.missing} == null", ctx));
        }

        @Test
        @DisplayName("数字字面量整数与小数")
        void numericLiterals() {
            assertTrue(engine.evaluateCondition("5 > 3", Map.of()));
            assertTrue(engine.evaluateCondition("3.14 > 3.0", Map.of()));
        }

        @Test
        @DisplayName("字符串字面量单引号与双引号")
        void stringLiterals() {
            assertTrue(engine.evaluateCondition("'abc' == 'abc'", Map.of()));
            assertTrue(engine.evaluateCondition("\"abc\" == \"abc\"", Map.of()));
        }

        @Test
        @DisplayName("布尔与 null 字面量")
        void booleanAndNullLiterals() {
            assertFalse(engine.evaluateCondition("true && false", Map.of()));
            assertTrue(engine.evaluateCondition("true || false", Map.of()));
            assertTrue(engine.evaluateCondition("null == null", Map.of()));
        }
    }

    @Nested
    @DisplayName("运算符")
    class Operators {

        @Test
        @DisplayName("比较运算符全套")
        void comparisonOps() {
            assertTrue(engine.evaluateCondition("5 >= 5", Map.of()));
            assertTrue(engine.evaluateCondition("4 <= 5", Map.of()));
            assertTrue(engine.evaluateCondition("5 != 4", Map.of()));
            assertFalse(engine.evaluateCondition("3 < 3", Map.of()));
        }

        @Test
        @DisplayName("逻辑非 !")
        void logicalNot() {
            assertFalse(engine.evaluateCondition("!true", Map.of()));
            assertTrue(engine.evaluateCondition("!false", Map.of()));
            assertTrue(engine.evaluateCondition("!(1 > 2)", Map.of()));
        }

        @Test
        @DisplayName("括号分组与逻辑组合")
        void parenthesesAndLogic() {
            assertTrue(engine.evaluateCondition("(1 > 0) && (2 > 1)", Map.of()));
            assertFalse(engine.evaluateCondition("(1 > 2) || (2 > 3)", Map.of()));
        }

        @Test
        @DisplayName("逻辑短路: && 左假不求右")
        void shortCircuitAnd() {
            // 右侧引用不存在的函数会抛异常，但 && 短路不会求值右侧
            assertDoesNotThrow(() -> engine.evaluateCondition("false && unknownFn(1)", Map.of()));
            assertFalse(engine.evaluateCondition("false && unknownFn(1)", Map.of()));
        }
    }

    @Nested
    @DisplayName("白名单函数")
    class WhitelistFunctions {

        @Test
        @DisplayName("数学: abs / round / ceil / floor / min / max")
        void mathFunctions() {
            assertEquals(5.0, d(engine.evaluate("abs(-5)", Map.of())), 0.0001);
            assertEquals(3.14, d(engine.evaluate("round(3.14159, 2)", Map.of())), 0.0001);
            assertEquals(3.0, d(engine.evaluate("ceil(2.1)", Map.of())), 0.0001);
            assertEquals(2.0, d(engine.evaluate("floor(2.9)", Map.of())), 0.0001);
            assertEquals(2.0, d(engine.evaluate("min(2, 5)", Map.of())), 0.0001);
            assertEquals(5.0, d(engine.evaluate("max(2, 5)", Map.of())), 0.0001);
        }

        @Test
        @DisplayName("字符串: upper / lower / length / concat / contains / substring")
        void stringFunctions() {
            assertEquals("ABC", engine.evaluate("upper('abc')", Map.of()));
            assertEquals("abc", engine.evaluate("lower('ABC')", Map.of()));
            assertEquals(3, i(engine.evaluate("length('abc')", Map.of())));
            assertEquals("ab", engine.evaluate("concat('a', 'b')", Map.of()));
            assertTrue((Boolean) engine.evaluate("contains('hello', 'ell')", Map.of()));
            assertEquals("hel", engine.evaluate("substring('hello', 0, 3)", Map.of()));
        }

        @Test
        @DisplayName("日期: now / formatDate / dateAdd / dateDiff")
        void dateFunctions() {
            String now = (String) engine.evaluate("now()", Map.of());
            assertNotNull(now);
            assertTrue(now.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
            assertEquals("2024/01/01", engine.evaluate("formatDate('2024-01-01', 'yyyy/MM/dd')", Map.of()));
            assertEquals("2024-01-06", engine.evaluate("dateAdd('2024-01-01', 5)", Map.of()));
            assertEquals(9, i(engine.evaluate("dateDiff('2024-01-10', '2024-01-01')", Map.of())));
        }

        @Test
        @DisplayName("空值: isNull / notNull / isEmpty / isNotEmpty")
        void nullFunctions() {
            assertTrue((Boolean) engine.evaluate("isNull(null)", Map.of()));
            assertTrue((Boolean) engine.evaluate("notNull('a')", Map.of()));
            assertTrue((Boolean) engine.evaluate("isEmpty('')", Map.of()));
            assertTrue((Boolean) engine.evaluate("isNotEmpty('a')", Map.of()));
            assertFalse((Boolean) engine.evaluate("isNotEmpty('')", Map.of()));
        }

        @Test
        @DisplayName("集合 size 与字典 dict")
        void sizeAndDict() {
            Map<String, Object> ctx = Map.of(
                    "form", Map.of("items", List.of(1, 2, 3)),
                    "dict", Map.of("status", "ACTIVE"));
            assertEquals(3, i(engine.evaluate("size(${form.items})", ctx)));
            assertEquals("ACTIVE", engine.evaluate("dict('status')", ctx));
        }

        @Test
        @DisplayName("三元函数 if(cond, a, b)")
        void ifFunction() {
            assertEquals("yes", engine.evaluate("if(1 > 0, 'yes', 'no')", Map.of()));
            assertEquals("no", engine.evaluate("if(1 > 2, 'yes', 'no')", Map.of()));
        }

        @Test
        @DisplayName("嵌套函数调用")
        void nestedFunctions() {
            assertEquals("Ab", engine.evaluate("concat(upper('a'), lower('B'))", Map.of()));
            assertEquals(2, i(engine.evaluate("length(upper('ab'))", Map.of())));
        }

        @Test
        @DisplayName("未知函数抛 BusinessException")
        void unknownFunctionThrows() {
            assertThrows(BusinessException.class, () -> engine.evaluate("unknownFn(1)", Map.of()));
        }
    }

    @Nested
    @DisplayName("安全: 超时 / 长度 / 危险关键字")
    class Security {

        @Test
        @DisplayName("超时熔断: now() 阻塞超过阈值抛 BusinessException")
        void timeoutCircuitBreaker() {
            WhiteListFunctions slow = new WhiteListFunctions() {
                @Override
                public Object now() {
                    try {
                        Thread.sleep(300);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "slow";
                }
            };
            ExpressionEngine slowEngine = new ExpressionEngine(slow);
            slowEngine.setTimeoutMs(30);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> slowEngine.evaluate("now()", Map.of()));
            assertNotNull(ex.customMessage());
            assertTrue(ex.customMessage().contains("超时"));
        }

        @Test
        @DisplayName("长度超限 (>1000 字符) 抛 BusinessException")
        void lengthExceeded() {
            String tooLong = "a".repeat(ExpressionEngine.MAX_LENGTH + 1);
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> engine.evaluate(tooLong, Map.of()));
            assertTrue(ex.customMessage().contains("长度超限"));
        }

        @Test
        @DisplayName("危险关键字 Runtime 被拦截")
        void dangerousRuntime() {
            assertThrows(BusinessException.class, () -> engine.evaluate("Runtime", Map.of()));
        }

        @Test
        @DisplayName("危险关键字 Class.forName 被拦截")
        void dangerousForName() {
            assertThrows(BusinessException.class, () -> engine.evaluate("Class.forName('x')", Map.of()));
        }

        @Test
        @DisplayName("危险关键字 .exec( 被拦截")
        void dangerousExec() {
            assertThrows(BusinessException.class, () -> engine.evaluate("a.exec('x')", Map.of()));
        }

        @Test
        @DisplayName("不支持裸属性访问 a.b (非法字符 .)")
        void barePropertyAccessRejected() {
            assertThrows(BusinessException.class, () -> engine.evaluate("form.amount", Map.of()));
        }
    }

    @Nested
    @DisplayName("语法校验 validate")
    class Validation {

        @Test
        @DisplayName("合法表达式返回 valid=true")
        void validExpression() {
            ExpressionEngine.ValidationResult vr = engine.validate("(${form.x} > 1) && contains(${form.s}, 'a')");
            assertTrue(vr.valid());
            assertNull(vr.error());
        }

        @Test
        @DisplayName("语法错误返回 valid=false 且带 error")
        void syntaxError() {
            ExpressionEngine.ValidationResult vr = engine.validate("1 >");
            assertFalse(vr.valid());
            assertNotNull(vr.error());
        }

        @Test
        @DisplayName("危险关键字返回 valid=false")
        void dangerousKeywordInvalid() {
            ExpressionEngine.ValidationResult vr = engine.validate("Runtime");
            assertFalse(vr.valid());
        }

        @Test
        @DisplayName("空表达式返回 valid=false")
        void blankExpressionInvalid() {
            ExpressionEngine.ValidationResult vr = engine.validate("   ");
            assertFalse(vr.valid());
        }
    }
}
