package com.yutong.infra.persistence;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 多租户行级隔离配置。设计来源: ADR 0004 P2-A (common-tenant)。
 *
 * <p>生效方式: {@link MyBatisPlusConfig} 首位注册 {@link YutongTenantLineHandler}，
 * 所有 MyBatis-Plus 生成 SQL 自动追加 {@code tenant_id = ?}。
 *
 * <p>安全语义:
 * <ul>
 *   <li>租户取 {@code CurrentUserContext.getTenantId()}；为空时本查询跳过租户条件
 *       (定时任务/系统线程无上下文，保持原行为);</li>
 *   <li>{@code ignore-tables} 豁免全局共享表 (默认空；所有 130 业务表均有 tenant_id 列);</li>
 *   <li>原生 JdbcTemplate/@Select SQL 不被拦截 (保持原行为)，跨租户读缺口另行收敛。</li>
 * </ul>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "yutong.tenant")
public class TenantProperties {

    /**
     * 行级隔离总开关 (默认 true)。
     * 关闭后回到显式 tenant 过滤模式 (存量查询均自带 tenant 条件，行为不变)。
     */
    private boolean interceptorEnabled = true;

    /**
     * 豁免表名 (小写比较)，这些表的查询不追加租户条件。
     * 默认空；如有真正全局共享表在此声明。
     */
    private List<String> ignoreTables = new ArrayList<>();
}
