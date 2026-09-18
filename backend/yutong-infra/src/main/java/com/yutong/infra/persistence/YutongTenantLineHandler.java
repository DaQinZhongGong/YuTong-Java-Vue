package com.yutong.infra.persistence;

import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.yutong.common.auth.CurrentUserContext;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * YuTong 租户行级处理器。设计来源: ADR 0004 P2-A (common-tenant)。
 *
 * <p>规则:
 * <ul>
 *   <li>租户列固定 {@code tenant_id};</li>
 *   <li>当前上下文无租户 (null/blank) → 所有表视为豁免, 整查询不追加租户条件
 *       (定时任务/系统线程/未认证内部调用保持原行为)。
 *       注意: MP 3.5.x 的 {@code getTenantId()} 返回 null 时仍会拼出
 *       {@code tenant_id = null} (永假), 不能靠返回 null 跳过,
 *       唯一跳过路径是 {@link #ignoreTable(String)};</li>
 *   <li>豁免表大小写不敏感匹配 {@link TenantProperties#getIgnoreTables()}。</li>
 * </ul>
 */
public class YutongTenantLineHandler implements TenantLineHandler {

    private final TenantProperties properties;
    private final Set<String> ignoreSet = new HashSet<>();

    public YutongTenantLineHandler(TenantProperties properties) {
        this.properties = properties;
        if (properties.getIgnoreTables() != null) {
            for (String t : properties.getIgnoreTables()) {
                if (t != null && !t.isBlank()) {
                    ignoreSet.add(t.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    @Override
    public Expression getTenantId() {
        String tenantId = currentTenant();
        // ignoreTable 已先行拦截无租户场景；此处兜底永不返回 null
        // (MP 会把 null 拼成 tenant_id = null 永假条件)
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "default";
        }
        return new StringValue(tenantId.trim());
    }

    @Override
    public String getTenantIdColumn() {
        return "tenant_id";
    }

    @Override
    public boolean ignoreTable(String tableName) {
        // 无租户上下文: 全表豁免 (唯一安全的跳过路径)
        if (isBlankTenant()) {
            return true;
        }
        if (tableName == null) return false;
        // MP 传小写表名居多；统一去引号+小写比较，quoted 大小写同样覆盖
        return ignoreSet.contains(normalizeTableName(tableName));
    }

    static String normalizeTableName(String tableName) {
        String t = tableName.trim().toLowerCase(Locale.ROOT);
        if (t.length() >= 2) {
            char first = t.charAt(0);
            char last = t.charAt(t.length() - 1);
            if ((first == '"' && last == '"')
                    || (first == '`' && last == '`')
                    || (first == '[' && last == ']')) {
                return t.substring(1, t.length() - 1);
            }
        }
        return t;
    }

    private boolean isBlankTenant() {
        return currentTenant() == null || currentTenant().isBlank();
    }

    private String currentTenant() {
        try {
            return CurrentUserContext.getTenantId();
        } catch (Exception e) {
            return null;
        }
    }
}
