package com.yutong.common.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataScopeType 枚举单元测试。设计来源: contracts/registries/permissions.yaml scopes.data
 * 覆盖:
 *  - 7 种 scopeType 枚举值与 permissions.yaml 完全一致
 *  - of() 大小写不敏感解析
 *  - of() 无效值返回 NONE（安全默认）
 *  - of() null/空字符串返回 NONE
 */
@DisplayName("DataScopeType 枚举")
class DataScopeTypeTest {

    @Nested
    @DisplayName("枚举值完整性")
    class EnumValues {
        @Test
        @DisplayName("应包含 7 种 scopeType")
        void containsAllSevenTypes() {
            // 对齐 contracts/registries/permissions.yaml scopes.data
            // [ALL, TENANT, DEPT_AND_CHILD, DEPT, SELF, CUSTOM, NONE]
            assertEquals(7, DataScopeType.values().length);
            assertNotNull(DataScopeType.ALL);
            assertNotNull(DataScopeType.TENANT);
            assertNotNull(DataScopeType.DEPT_AND_CHILD);
            assertNotNull(DataScopeType.DEPT);
            assertNotNull(DataScopeType.SELF);
            assertNotNull(DataScopeType.CUSTOM);
            assertNotNull(DataScopeType.NONE);
        }
    }

    @Nested
    @DisplayName("of() 解析")
    class OfParsing {
        @Test
        @DisplayName("大写值正确解析")
        void upperCase() {
            assertEquals(DataScopeType.ALL, DataScopeType.of("ALL"));
            assertEquals(DataScopeType.TENANT, DataScopeType.of("TENANT"));
            assertEquals(DataScopeType.DEPT_AND_CHILD, DataScopeType.of("DEPT_AND_CHILD"));
            assertEquals(DataScopeType.DEPT, DataScopeType.of("DEPT"));
            assertEquals(DataScopeType.SELF, DataScopeType.of("SELF"));
            assertEquals(DataScopeType.CUSTOM, DataScopeType.of("CUSTOM"));
            assertEquals(DataScopeType.NONE, DataScopeType.of("NONE"));
        }

        @Test
        @DisplayName("小写值正确解析（大小写不敏感）")
        void lowerCase() {
            assertEquals(DataScopeType.ALL, DataScopeType.of("all"));
            assertEquals(DataScopeType.CUSTOM, DataScopeType.of("custom"));
            assertEquals(DataScopeType.NONE, DataScopeType.of("none"));
        }

        @Test
        @DisplayName("混合大小写正确解析")
        void mixedCase() {
            assertEquals(DataScopeType.ALL, DataScopeType.of("All"));
            assertEquals(DataScopeType.DEPT_AND_CHILD, DataScopeType.of("Dept_And_Child"));
        }

        @Test
        @DisplayName("前后空格自动 trim")
        void trimSpaces() {
            assertEquals(DataScopeType.ALL, DataScopeType.of("  ALL  "));
            assertEquals(DataScopeType.CUSTOM, DataScopeType.of("\tcustom\n"));
        }
    }

    @Nested
    @DisplayName("安全默认")
    class SafeDefaults {
        @Test
        @DisplayName("null 值 → NONE（安全默认）")
        void nullReturnsNone() {
            assertEquals(DataScopeType.NONE, DataScopeType.of(null));
        }

        @Test
        @DisplayName("空字符串 → NONE")
        void emptyStringReturnsNone() {
            assertEquals(DataScopeType.NONE, DataScopeType.of(""));
        }

        @Test
        @DisplayName("空白字符串 → NONE")
        void blankStringReturnsNone() {
            assertEquals(DataScopeType.NONE, DataScopeType.of("   "));
        }

        @Test
        @DisplayName("无效值 → NONE（安全默认，不抛异常）")
        void invalidReturnsNone() {
            assertEquals(DataScopeType.NONE, DataScopeType.of("INVALID"));
            assertEquals(DataScopeType.NONE, DataScopeType.of("DEPT_TREE"));  // 历史值不再使用
            assertEquals(DataScopeType.NONE, DataScopeType.of("123"));
        }
    }
}
