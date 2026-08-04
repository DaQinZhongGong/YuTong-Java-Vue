package com.yutong.auth;

import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.CurrentUserContext;
import org.springframework.stereotype.Component;

/**
 * Mock 数据权限解析器。委托 AuthAdapter.getDataScope 实现。
 * 设计来源: 67-数据权限与审计日志详设 第 41 行
 *
 * GA2-02 实现:
 *  - 通过 AuthAdapter.getDataScope(userId, tenantId, resourceCode) 获取 DataScope
 *  - userId/tenantId 从 CurrentUserContext 读取（已由 TraceAuthFilter 透传）
 *  - 与 MockAuthAdapter 的 4 类用户切换对齐
 *
 * 生产环境可替换为基于 sys_role_data_scope 表的实现。
 */
@Component
public class MockDataScopeResolver implements DataScopeResolver {

    private final AuthAdapter authAdapter;

    public MockDataScopeResolver(AuthAdapter authAdapter) {
        this.authAdapter = authAdapter;
    }

    @Override
    public DataScope resolve(String resourceCode) {
        String userId = CurrentUserContext.getUserId();
        String tenantId = CurrentUserContext.getTenantId();
        if (userId == null || userId.isBlank()) {
            // 未登录 → NONE
            return DataScope.none(null, tenantId, resourceCode);
        }
        return authAdapter.getDataScope(userId, tenantId, resourceCode);
    }
}
