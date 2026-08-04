package com.yutong.infra.persistence;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yutong.common.auth.DataScope;

import java.util.Set;

/**
 * 数据权限 SQL 过滤器。设计来源: 67-数据权限与审计日志详设 第 43-54 行 SQL 转换规则
 *
 * 将 DataScope 转换为 MyBatis-Plus QueryWrapper 条件。
 * 调用方需先在 wrapper 上添加 tenant_id = ? 条件，本过滤器只追加 scope 相关条件。
 *
 * 转换规则:
 *  - ALL             → 无附加条件
 *  - TENANT          → 无附加条件（tenant_id 已由调用方添加）
 *  - DEPT_AND_CHILD  → owner_dept_path LIKE 'prefix%' (多个 prefix 用 OR)
 *  - DEPT            → owner_dept_id IN (:deptIds)
 *  - SELF            → owner_user_id = currentUser
 *  - CUSTOM          → id IN (:resourceIds)；白名单为空时追加 1=0 返回空结果
 *  - NONE            → 追加 1=0 返回空结果
 *
 * 放置在 yutong-infra 模块（依赖 MyBatis-Plus），yutong-common 保持纯 Java 基础类。
 */
public final class DataScopeFilter {

    /** id 列名（主键），默认 "id"。 */
    public static final String DEFAULT_ID_COLUMN = "id";
    /** 归属用户 ID 列名，默认 "owner_user_id"。 */
    public static final String DEFAULT_OWNER_USER_COLUMN = "owner_user_id";
    /** 归属部门 ID 列名，默认 "owner_dept_id"。 */
    public static final String DEFAULT_OWNER_DEPT_COLUMN = "owner_dept_id";
    /** 归属部门路径列名，默认 "owner_dept_path"。 */
    public static final String DEFAULT_OWNER_DEPT_PATH_COLUMN = "owner_dept_path";

    private DataScopeFilter() {}

    /**
     * 将 DataScope 应用到 QueryWrapper，使用默认列名。
     *
     * @param wrapper   QueryWrapper 实例
     * @param dataScope 数据权限范围
     * @param <T>       实体类型
     */
    public static <T> void apply(QueryWrapper<T> wrapper, DataScope dataScope) {
        apply(wrapper, dataScope, null);
    }

    /**
     * 将 DataScope 应用到 QueryWrapper，可指定表别名（如 "r" for biz_request r）。
     *
     * @param wrapper    QueryWrapper 实例
     * @param dataScope  数据权限范围
     * @param tableAlias 表别名，可为 null
     * @param <T>        实体类型
     */
    public static <T> void apply(QueryWrapper<T> wrapper, DataScope dataScope, String tableAlias) {
        if (wrapper == null || dataScope == null) {
            return;
        }
        String prefix = (tableAlias == null || tableAlias.isBlank()) ? "" : tableAlias + ".";
        String idCol = prefix + DEFAULT_ID_COLUMN;
        String ownerUserCol = prefix + DEFAULT_OWNER_USER_COLUMN;
        String ownerDeptCol = prefix + DEFAULT_OWNER_DEPT_COLUMN;
        String ownerDeptPathCol = prefix + DEFAULT_OWNER_DEPT_PATH_COLUMN;

        switch (dataScope.scopeType()) {
            case ALL:
                // 无附加条件
                break;
            case TENANT:
                // tenant_id 已由调用方添加，这里无附加条件
                break;
            case DEPT:
                Set<String> deptIds = dataScope.deptIds();
                if (deptIds == null || deptIds.isEmpty()) {
                    // 部门集合为空 → 返回空结果（安全默认）
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.in(ownerDeptCol, deptIds);
                }
                break;
            case DEPT_AND_CHILD:
                Set<String> pathPrefixes = dataScope.deptPathPrefixes();
                if (pathPrefixes == null || pathPrefixes.isEmpty()) {
                    wrapper.apply("1 = 0");
                } else {
                    // 多个前缀用 OR 拼接: (owner_dept_path LIKE 'p1%' OR owner_dept_path LIKE 'p2%')
                    wrapper.and(w -> {
                        boolean first = true;
                        for (String prefix2 : pathPrefixes) {
                            if (prefix2 == null || prefix2.isBlank()) {
                                continue;
                            }
                            if (first) {
                                w.likeRight(ownerDeptPathCol, prefix2);
                                first = false;
                            } else {
                                w.or().likeRight(ownerDeptPathCol, prefix2);
                            }
                        }
                        if (first) {
                            // 全部前缀都为空 → 返回空结果
                            w.apply("1 = 0");
                        }
                    });
                }
                break;
            case SELF:
                String userId = dataScope.userId();
                if (userId == null || userId.isBlank()) {
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.eq(ownerUserCol, userId);
                }
                break;
            case CUSTOM:
                Set<String> resourceIds = dataScope.resourceIds();
                if (resourceIds == null || resourceIds.isEmpty()) {
                    // 67 号文档第 52 行硬约束: 白名单为空 → 返回空结果，严禁降级为 ALL
                    wrapper.apply("1 = 0");
                } else {
                    wrapper.in(idCol, resourceIds);
                }
                break;
            case NONE:
                // 67 号文档第 52 行: 1 = 0 或等价的不可满足条件，必须返回空结果
                wrapper.apply("1 = 0");
                break;
        }
    }
}
