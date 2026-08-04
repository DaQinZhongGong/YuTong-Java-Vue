package com.yutong.common.auth;

import java.util.Collections;
import java.util.Set;

/**
 * 数据权限范围。设计来源: 67-数据权限与审计日志详设 第 27-39 行
 *
 * record 字段:
 *  - scopeType          范围类型 ALL/TENANT/DEPT_AND_CHILD/DEPT/SELF/CUSTOM/NONE
 *  - userId             当前用户 ID
 *  - tenantId           当前租户 ID
 *  - resourceCode       资源编码，如 biz:request、sys:file
 *  - deptIds            部门 ID 集合（DEPT/DEPT_AND_CHILD 使用）
 *  - deptPathPrefixes   部门路径前缀集合（DEPT_AND_CHILD 使用）
 *  - resourceIds        资源 ID 白名单（CUSTOM 使用，空集合时空结果严禁降级）
 *  - ownerUserIds       归属用户集合（SELF 使用）
 *  - includeSensitive   是否允许查看敏感字段（脱敏开关，viewer=false）
 *
 * AuthAdapter#getDataScope(userId, tenantId, resourceCode) 是唯一数据来源。
 * Repository 层负责把 DataScope 转成 SQL 条件；ApplicationService 负责选择 resourceCode。
 */
public record DataScope(
        DataScopeType scopeType,
        String userId,
        String tenantId,
        String resourceCode,
        Set<String> deptIds,
        Set<String> deptPathPrefixes,
        Set<String> resourceIds,
        Set<String> ownerUserIds,
        boolean includeSensitive
) {

    /** ALL 范围工厂方法。 */
    public static DataScope all(String userId, String tenantId, String resourceCode) {
        return new DataScope(DataScopeType.ALL, userId, tenantId, resourceCode,
                Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), true);
    }

    /** TENANT 范围工厂方法（默认 authenticated scope）。 */
    public static DataScope tenant(String userId, String tenantId, String resourceCode) {
        return new DataScope(DataScopeType.TENANT, userId, tenantId, resourceCode,
                Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), true);
    }

    /** SELF 范围工厂方法（仅本人数据）。userId 为 null/空时返回空 ownerUserIds（DataScopeFilter 会降级为 1=0）。 */
    public static DataScope self(String userId, String tenantId, String resourceCode) {
        Set<String> ownerUserIds = (userId == null || userId.isBlank())
                ? Collections.emptySet()
                : Set.of(userId);
        return new DataScope(DataScopeType.SELF, userId, tenantId, resourceCode,
                Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), ownerUserIds, true);
    }

    /** DEPT 范围工厂方法（本部门数据）。 */
    public static DataScope dept(String userId, String tenantId, String resourceCode, Set<String> deptIds) {
        return new DataScope(DataScopeType.DEPT, userId, tenantId, resourceCode,
                deptIds == null ? Collections.emptySet() : deptIds,
                Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), true);
    }

    /** DEPT_AND_CHILD 范围工厂方法（本部门及下级，按 path 前缀过滤）。 */
    public static DataScope deptAndChild(String userId, String tenantId, String resourceCode,
                                          Set<String> deptPathPrefixes) {
        return new DataScope(DataScopeType.DEPT_AND_CHILD, userId, tenantId, resourceCode,
                Collections.emptySet(),
                deptPathPrefixes == null ? Collections.emptySet() : deptPathPrefixes,
                Collections.emptySet(), Collections.emptySet(), true);
    }

    /**
     * CUSTOM 范围工厂方法（白名单 resourceIds）。
     * 注意: resourceIds 为空集合时表示"白名单为空 → 返回空结果，严禁降级为 ALL"（67 号文档第 52 行硬约束）。
     */
    public static DataScope custom(String userId, String tenantId, String resourceCode,
                                    Set<String> resourceIds, boolean includeSensitive) {
        return new DataScope(DataScopeType.CUSTOM, userId, tenantId, resourceCode,
                Collections.emptySet(), Collections.emptySet(),
                resourceIds == null ? Collections.emptySet() : resourceIds,
                Collections.emptySet(), includeSensitive);
    }

    /** NONE 范围工厂方法（拒绝全部业务数据）。 */
    public static DataScope none(String userId, String tenantId, String resourceCode) {
        return new DataScope(DataScopeType.NONE, userId, tenantId, resourceCode,
                Collections.emptySet(), Collections.emptySet(),
                Collections.emptySet(), Collections.emptySet(), false);
    }

    /** 判断当前范围是否允许查看敏感字段。 */
    public boolean canViewSensitive() {
        return includeSensitive && scopeType != DataScopeType.NONE;
    }
}
