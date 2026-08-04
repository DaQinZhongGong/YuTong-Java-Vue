package com.yutong.common.auth;

/**
 * 数据权限范围类型。设计来源: 67-数据权限与审计日志详设、contracts/registries/permissions.yaml
 * 与 permissions.yaml scopes.data 完全一致: ALL/TENANT/DEPT_AND_CHILD/DEPT/SELF/CUSTOM/NONE
 *
 * SQL 转换规则:
 *  - ALL             → 无附加条件
 *  - TENANT          → tenant_id = currentTenant (默认 authenticated scope)
 *  - DEPT_AND_CHILD  → owner_dept_path like 'prefix%'
 *  - DEPT            → owner_dept_id = currentDept
 *  - SELF            → owner_user_id = currentUser
 *  - CUSTOM          → id in (:resourceIds)；白名单为空时返回空结果，严禁降级为 ALL
 *  - NONE            → 1 = 0 不可满足条件，必须返回空结果，禁止回退 TENANT/ALL
 */
public enum DataScopeType {
    ALL,
    TENANT,
    DEPT_AND_CHILD,
    DEPT,
    SELF,
    CUSTOM,
    NONE;

    /**
     * 大小写不敏感解析，无效值返回 NONE（安全默认）。
     */
    public static DataScopeType of(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return DataScopeType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
