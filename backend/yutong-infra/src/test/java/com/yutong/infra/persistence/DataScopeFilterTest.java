package com.yutong.infra.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yutong.common.auth.DataScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DataScopeFilter 单元测试。设计来源: 67-数据权限与审计日志详设 第 43-54 行 SQL 转换规则
 * 覆盖 7 种 scopeType 的 QueryWrapper 条件拼接:
 *  - ALL             → 无附加条件
 *  - TENANT          → 无附加条件
 *  - DEPT            → owner_dept_id IN (:deptIds)
 *  - DEPT_AND_CHILD  → owner_dept_path LIKE 'prefix%'
 *  - SELF            → owner_user_id = currentUser
 *  - CUSTOM          → id IN (:resourceIds)；空集 → 1=0
 *  - NONE            → 1=0
 *
 * 重点测试 67 号文档第 52 行硬约束:
 *  - CUSTOM 白名单为空 → 返回空结果，严禁降级为 ALL
 *  - NONE → 1=0 不可满足条件
 */
@DisplayName("DataScopeFilter SQL 转换")
class DataScopeFilterTest {

    @Nested
    @DisplayName("ALL 范围")
    class AllScope {
        @Test
        @DisplayName("ALL → 无附加条件")
        void allNoCondition() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.all("u1", "t1", "biz:request");
            DataScopeFilter.apply(wrapper, scope);
            // ALL 不应附加任何条件
            String sql = wrapper.getTargetSql();
            assertEquals("", sql.trim());
        }

        @Test
        @DisplayName("ALL + 表别名 → 无附加条件")
        void allWithAliasNoCondition() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.all("u1", "t1", "biz:request");
            DataScopeFilter.apply(wrapper, scope, "r");
            String sql = wrapper.getTargetSql();
            assertEquals("", sql.trim());
        }
    }

    @Nested
    @DisplayName("TENANT 范围")
    class TenantScope {
        @Test
        @DisplayName("TENANT → 无附加条件（tenant_id 由调用方添加）")
        void tenantNoCondition() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.tenant("u1", "t1", "biz:request");
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            assertEquals("", sql.trim());
        }
    }

    @Nested
    @DisplayName("DEPT 范围")
    class DeptScope {
        @Test
        @DisplayName("DEPT → owner_dept_id IN (:deptIds)")
        void deptInCondition() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.dept("u1", "t1", "biz:request", Set.of("dept-001", "dept-002"));
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            // 应包含 owner_dept_id IN 条件
            assertTrue(sql.contains("owner_dept_id"), () -> "SQL 应包含 owner_dept_id: " + sql);
            assertTrue(sql.contains("IN"), () -> "SQL 应包含 IN: " + sql);
        }

        @Test
        @DisplayName("DEPT + 空部门集合 → 1=0 空结果")
        void deptEmptySetReturnsEmpty() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.dept("u1", "t1", "biz:request", Set.of());
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            // 空部门集合 → 安全默认空结果
            assertTrue(sql.contains("1 = 0") || sql.contains("1=0"), () -> "空部门应返回 1=0: " + sql);
        }
    }

    @Nested
    @DisplayName("DEPT_AND_CHILD 范围")
    class DeptAndChildScope {
        @Test
        @DisplayName("DEPT_AND_CHILD → owner_dept_path LIKE 'prefix%'")
        void deptAndChildLikeCondition() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.deptAndChild("u1", "t1", "biz:request", Set.of("/corp/sales"));
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            // 应包含 owner_dept_path LIKE 条件
            assertTrue(sql.contains("owner_dept_path"), () -> "SQL 应包含 owner_dept_path: " + sql);
            assertTrue(sql.contains("LIKE"), () -> "SQL 应包含 LIKE: " + sql);
        }

        @Test
        @DisplayName("DEPT_AND_CHILD + 空前缀集合 → 1=0 空结果")
        void deptAndChildEmptyPrefixReturnsEmpty() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.deptAndChild("u1", "t1", "biz:request", Set.of());
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            assertTrue(sql.contains("1 = 0") || sql.contains("1=0"), () -> "空前缀应返回 1=0: " + sql);
        }
    }

    @Nested
    @DisplayName("SELF 范围")
    class SelfScope {
        @Test
        @DisplayName("SELF → owner_user_id = currentUser (参数化 SQL)")
        void selfEqUser() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.self("u-self-001", "t1", "biz:request");
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            // QueryWrapper 使用参数化查询，getTargetSql() 返回 "owner_user_id = ?"
            assertTrue(sql.contains("owner_user_id"), () -> "SQL 应包含 owner_user_id 列: " + sql);
            assertTrue(sql.contains("="), () -> "SQL 应包含 = 运算符: " + sql);
            // 参数值通过 getParamNameValuePairs() 获取
            assertTrue(wrapper.getParamNameValuePairs().containsValue("u-self-001"),
                    () -> "参数值应包含 u-self-001: " + wrapper.getParamNameValuePairs());
        }

        @Test
        @DisplayName("SELF + userId 为空 → 1=0 空结果")
        void selfNullUserReturnsEmpty() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            // DataScope.self(null, ...) 不应抛 NPE，返回空 ownerUserIds
            DataScope scope = assertDoesNotThrow(() -> DataScope.self(null, "t1", "biz:request"));
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            assertTrue(sql.contains("1 = 0") || sql.contains("1=0"), () -> "空 userId 应返回 1=0: " + sql);
        }

        @Test
        @DisplayName("SELF + userId 为空字符串 → 1=0 空结果")
        void selfBlankUserReturnsEmpty() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.self("  ", "t1", "biz:request");
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            assertTrue(sql.contains("1 = 0") || sql.contains("1=0"), () -> "空白 userId 应返回 1=0: " + sql);
        }
    }

    @Nested
    @DisplayName("CUSTOM 范围")
    class CustomScope {
        @Test
        @DisplayName("CUSTOM + 非空白名单 → id IN (:resourceIds)")
        void customInCondition() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.custom("u1", "t1", "biz:request",
                    Set.of("r-001", "r-002"), true);
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            assertTrue(sql.contains("id"), () -> "SQL 应包含 id 列: " + sql);
            assertTrue(sql.contains("IN"), () -> "SQL 应包含 IN: " + sql);
        }

        @Test
        @DisplayName("CUSTOM + 空白名单 → 1=0 空结果（67 号文档第 52 行硬约束，严禁降级 ALL）")
        void customEmptyWhitelistReturnsEmpty() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.custom("u1", "t1", "biz:request", Set.of(), true);
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            // 关键测试: 空白名单 → 1=0，严禁降级为 ALL
            assertTrue(sql.contains("1 = 0") || sql.contains("1=0"),
                    () -> "空白名单必须返回 1=0，严禁降级为 ALL: " + sql);
        }

        @Test
        @DisplayName("CUSTOM + null 白名单 → 1=0 空结果")
        void customNullWhitelistReturnsEmpty() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.custom("u1", "t1", "biz:request", null, true);
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            assertTrue(sql.contains("1 = 0") || sql.contains("1=0"),
                    () -> "null 白名单必须返回 1=0: " + sql);
        }
    }

    @Nested
    @DisplayName("NONE 范围")
    class NoneScope {
        @Test
        @DisplayName("NONE → 1=0 不可满足条件")
        void noneReturnsEmpty() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.none("u1", "t1", "biz:request");
            DataScopeFilter.apply(wrapper, scope);
            String sql = wrapper.getTargetSql();
            // 关键测试: NONE → 1=0，禁止回退 TENANT/ALL
            assertTrue(sql.contains("1 = 0") || sql.contains("1=0"),
                    () -> "NONE 必须返回 1=0，禁止回退 TENANT/ALL: " + sql);
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {
        @Test
        @DisplayName("null wrapper → 无 NPE，无操作")
        void nullWrapperNoOp() {
            DataScope scope = DataScope.all("u1", "t1", "biz:request");
            assertDoesNotThrow(() -> DataScopeFilter.apply(null, scope));
        }

        @Test
        @DisplayName("null dataScope → 无 NPE，无操作")
        void nullScopeNoOp() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            assertDoesNotThrow(() -> DataScopeFilter.apply(wrapper, null));
            assertEquals("", wrapper.getTargetSql().trim());
        }

        @Test
        @DisplayName("表别名应用: DEPT → alias.owner_dept_id IN")
        void deptWithAlias() {
            QueryWrapper<Object> wrapper = new QueryWrapper<>();
            DataScope scope = DataScope.dept("u1", "t1", "biz:request", Set.of("dept-001"));
            DataScopeFilter.apply(wrapper, scope, "r");
            String sql = wrapper.getTargetSql();
            assertTrue(sql.contains("r.owner_dept_id"), () -> "SQL 应包含表别名 r.owner_dept_id: " + sql);
        }
    }
}
