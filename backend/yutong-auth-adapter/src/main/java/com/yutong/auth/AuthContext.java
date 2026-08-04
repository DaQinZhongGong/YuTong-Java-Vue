package com.yutong.auth;

import java.util.Set;

/**
 * 认证上下文。登录后由 AuthAdapter 生成，贯穿整个请求。
 * 设计来源: 98-后端实现蓝图 AuthContext 与租户解析
 * 字段全部使用字符串 ID；mock=true 表示 Mock 鉴权，仅 local/test 可用。
 */
public record AuthContext(
        String userId,
        String username,
        String tenantId,
        Set<String> permissions,
        Set<String> roles,
        String dataScopeType,
        String deptId,
        String deptPath,
        boolean mock
) {
    public boolean hasPermission(String code) {
        return permissions != null && permissions.contains(code);
    }

    public boolean isMock() { return mock; }
}
