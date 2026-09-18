package com.yutong.infra.persistence;

import com.yutong.common.auth.CurrentUserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 租户行级处理器单测 (无容器)。
 * 设计来源: ADR 0004 P2-A (common-tenant)。
 *
 * 覆盖:
 * - 有租户 → StringValue 表达式
 * - 无租户 (null/blank) → null (MP 跳过本查询租户条件)
 * - 豁免表大小写不敏感
 * - 租户列固定 tenant_id
 */
@DisplayName("YutongTenantLineHandler")
class YutongTenantLineHandlerTest {

    @AfterEach
    void clear() {
        CurrentUserContext.clear();
    }

    private TenantProperties props(String... ignores) {
        TenantProperties p = new TenantProperties();
        p.setIgnoreTables(ignores == null ? List.of() : List.of(ignores));
        return p;
    }

    @Test
    @DisplayName("有租户返回 StringValue")
    void expressionWithTenant() {
        CurrentUserContext.set("u1", "t1", "n1");
        YutongTenantLineHandler handler = new YutongTenantLineHandler(props());

        assertEquals("'t1'", handler.getTenantId().toString());
    }

    @Test
    @DisplayName("无租户时全表豁免 (MP 唯一跳过路径, 不可返回 null 表达式)")
    void nullTenantIgnoresAllTables() {
        YutongTenantLineHandler handler = new YutongTenantLineHandler(props());

        assertTrue(handler.ignoreTable("ai_provider"));
        assertTrue(handler.ignoreTable("sys_license"));
    }

    @Test
    @DisplayName("空租户时全表豁免 (失败开放)")
    void blankTenantIgnoresAllTables() {
        CurrentUserContext.set("u1", "  ", "n1");
        YutongTenantLineHandler handler = new YutongTenantLineHandler(props());

        assertTrue(handler.ignoreTable("ai_provider"));
    }

    @Test
    @DisplayName("有租户时按豁免表判断")
    void ignoreListWithTenant() {
        CurrentUserContext.set("u1", "t1", "n1");
        YutongTenantLineHandler handler =
                new YutongTenantLineHandler(props("Sys_Config"));

        assertFalse(handler.ignoreTable("ai_provider"));
        assertTrue(handler.ignoreTable("sys_config"));
    }

    @Test
    @DisplayName("租户列固定 tenant_id")
    void columnName() {
        assertEquals("tenant_id", new YutongTenantLineHandler(props()).getTenantIdColumn());
    }

    @Test
    @DisplayName("豁免表大小写不敏感")
    void ignoreCaseInsensitive() {
        CurrentUserContext.set("u1", "t1", "n1");
        YutongTenantLineHandler handler =
                new YutongTenantLineHandler(props("Sys_Config", "flyway_schema_history"));

        assertTrue(handler.ignoreTable("sys_config"));
        assertTrue(handler.ignoreTable("SYS_CONFIG"));
        assertTrue(handler.ignoreTable("Flyway_Schema_History"));
        assertFalse(handler.ignoreTable("ai_provider"));
        assertFalse(handler.ignoreTable(null));
    }

    @Test
    @DisplayName("默认无豁免表")
    void noIgnoresByDefault() {
        TenantProperties defaults = new TenantProperties();

        assertTrue(defaults.isInterceptorEnabled());
        assertTrue(defaults.getIgnoreTables().isEmpty());
        CurrentUserContext.set("u1", "t1", "n1");
        assertFalse(new YutongTenantLineHandler(defaults).ignoreTable("ai_provider"));
    }

    @Test
    @DisplayName("带引号表名同样豁免")
    void ignoreQuotedTableNames() {
        CurrentUserContext.set("u1", "t1", "n1");
        YutongTenantLineHandler handler =
                new YutongTenantLineHandler(props("sys_config"));

        assertTrue(handler.ignoreTable("\"sys_config\""));
        assertTrue(handler.ignoreTable("`sys_config`"));
        assertTrue(handler.ignoreTable("[sys_config]"));
        assertFalse(handler.ignoreTable("\"ai_provider\""));
    }
}
