package com.yutong.lowcode.rule.engine;

import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 白名单函数单元测试。设计来源: 36-低代码高级能力设计 (line 72)。
 *
 * <p>直接调用 {@link WhiteListFunctions} 方法验证每个函数语义 (含类型转换)。
 */
@DisplayName("白名单函数集合")
class WhiteListFunctionsTest {

    private final WhiteListFunctions f = new WhiteListFunctions();

    @AfterEach
    void clearCtx() {
        WhiteListFunctions.clearContext();
    }

    private static double d(Object o) {
        return ((Number) o).doubleValue();
    }

    private static int i(Object o) {
        return ((Number) o).intValue();
    }

    @Nested
    @DisplayName("数学函数")
    class Math {

        @Test
        @DisplayName("abs: 负数取绝对值")
        void abs() {
            assertEquals(5.0, d(f.abs(-5)), 0.0001);
            assertEquals(5.0, d(f.abs(5)), 0.0001);
        }

        @Test
        @DisplayName("round: 四舍五入到指定小数位")
        void round() {
            assertEquals(3.14, d(f.round(3.14159, 2)), 0.0001);
            assertEquals(4.0, d(f.round(3.5, 0)), 0.0001);
        }

        @Test
        @DisplayName("ceil / floor")
        void ceilFloor() {
            assertEquals(3.0, d(f.ceil(2.1)), 0.0001);
            assertEquals(2.0, d(f.floor(2.9)), 0.0001);
        }

        @Test
        @DisplayName("min / max")
        void minMax() {
            assertEquals(2.0, d(f.min(2, 5)), 0.0001);
            assertEquals(5.0, d(f.max(2, 5)), 0.0001);
        }

        @Test
        @DisplayName("字符串数字入参也可运算")
        void stringNumericArg() {
            assertEquals(5.0, d(f.abs("-5")), 0.0001);
        }
    }

    @Nested
    @DisplayName("字符串函数")
    class Strings {

        @Test
        @DisplayName("upper / lower / trim")
        void caseAndTrim() {
            assertEquals("ABC", f.upper("abc"));
            assertEquals("abc", f.lower("ABC"));
            assertEquals("a", f.trim("  a  "));
        }

        @Test
        @DisplayName("length / concat")
        void lengthAndConcat() {
            assertEquals(5, i(f.length("hello")));
            assertEquals("ab", f.concat("a", "b"));
        }

        @Test
        @DisplayName("substring / replace")
        void substringAndReplace() {
            assertEquals("hel", f.substring("hello", 0, 3));
            assertEquals("hexxo", f.replace("hello", "l", "x"));
        }

        @Test
        @DisplayName("contains / indexOf / startsWith / endsWith")
        void searchFunctions() {
            assertTrue((Boolean) f.contains("hello", "ell"));
            assertEquals(1, i(f.indexOf("hello", "ell")));
            assertTrue((Boolean) f.startsWith("hello", "he"));
            assertTrue((Boolean) f.endsWith("hello", "lo"));
            assertFalse((Boolean) f.contains("hello", "z"));
        }

        @Test
        @DisplayName("null 入参安全转为空串")
        void nullArgToEmpty() {
            assertEquals("", f.upper(null));
            assertEquals(0, i(f.length(null)));
        }
    }

    @Nested
    @DisplayName("日期函数")
    class Dates {

        @Test
        @DisplayName("now 返回 yyyy-MM-dd HH:mm:ss")
        void now() {
            String now = (String) f.now();
            assertNotNull(now);
            assertTrue(now.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
        }

        @Test
        @DisplayName("formatDate 重新格式化")
        void formatDate() {
            assertEquals("2024/01/01", f.formatDate("2024-01-01", "yyyy/MM/dd"));
            assertEquals("2024", f.formatDate("2024-01-01 10:30:00", "yyyy"));
        }

        @Test
        @DisplayName("dateAdd 日期加天数")
        void dateAdd() {
            assertEquals("2024-01-06", f.dateAdd("2024-01-01", 5));
            assertEquals("2023-12-31", f.dateAdd("2024-01-01", -1));
        }

        @Test
        @DisplayName("dateDiff 日期差 (d1 - d2)")
        void dateDiff() {
            assertEquals(9, i(f.dateDiff("2024-01-10", "2024-01-01")));
            assertEquals(-9, i(f.dateDiff("2024-01-01", "2024-01-10")));
        }

        @Test
        @DisplayName("非法日期格式抛 BusinessException")
        void invalidDateThrows() {
            assertThrows(BusinessException.class, () -> f.formatDate("2024/01/01", "yyyy"));
        }
    }

    @Nested
    @DisplayName("字典 / 空值 / 集合 / 逻辑")
    class Misc {

        @Test
        @DisplayName("dict 从上下文 dict Map 取值")
        void dict() {
            Map<String, Object> ctx = new LinkedHashMap<>();
            ctx.put("dict", Map.of("status", "ACTIVE", "level", 3));
            WhiteListFunctions.setContext(ctx);
            assertEquals("ACTIVE", f.dict("status"));
            assertEquals(3, i(f.dict("level")));
            assertNull(f.dict("missing"));
        }

        @Test
        @DisplayName("dict 无上下文返回 null")
        void dictNoContext() {
            assertNull(f.dict("any"));
        }

        @Test
        @DisplayName("isNull / notNull")
        void isNullNotNull() {
            assertTrue((Boolean) f.isNull(null));
            assertFalse((Boolean) f.isNull("a"));
            assertTrue((Boolean) f.notNull("a"));
            assertFalse((Boolean) f.notNull(null));
        }

        @Test
        @DisplayName("isEmpty / isNotEmpty (null/空串/空集合)")
        void emptyChecks() {
            assertTrue((Boolean) f.isEmpty(null));
            assertTrue((Boolean) f.isEmpty(""));
            assertTrue((Boolean) f.isEmpty(List.of()));
            assertTrue((Boolean) f.isEmpty(Map.of()));
            assertFalse((Boolean) f.isEmpty("a"));
            assertTrue((Boolean) f.isNotEmpty("a"));
            assertFalse((Boolean) f.isNotEmpty(""));
        }

        @Test
        @DisplayName("size (集合/Map/字符串)")
        void size() {
            assertEquals(3, i(f.size(List.of(1, 2, 3))));
            assertEquals(2, i(f.size(Map.of("a", 1, "b", 2))));
            assertEquals(5, i(f.size("hello")));
            assertEquals(0, i(f.size(null)));
        }

        @Test
        @DisplayName("size 不支持的类型抛 BusinessException")
        void sizeUnsupportedThrows() {
            assertThrows(BusinessException.class, () -> f.size(123));
        }

        @Test
        @DisplayName("iif 三元")
        void iif() {
            assertEquals("yes", f.iif(true, "yes", "no"));
            assertEquals("no", f.iif(false, "yes", "no"));
            assertEquals("yes", f.iif(1, "yes", "no"));
            assertEquals("no", f.iif(0, "yes", "no"));
        }
    }
}
