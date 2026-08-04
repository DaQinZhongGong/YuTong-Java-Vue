package com.yutong.auth;

import com.yutong.common.auth.DataScope;

/**
 * 数据权限解析器。设计来源: contracts/registries/permissions.yaml 第 8 行、67-数据权限与审计日志详设 第 41 行
 *
 * ApplicationService 通过本接口获取 DataScope，由 Repository 层转换为 SQL 条件。
 * 实现类可委托 AuthAdapter.getDataScope，也可基于其他数据源（如 sys_role_data_scope 表）。
 *
 * 注意: resourceCode 由后端 ApplicationService 决定，不接受前端传入（67 号文档第 41 行）。
 */
public interface DataScopeResolver {

    /**
     * 解析当前用户对指定资源的数据权限范围。
     *
     * @param resourceCode 资源编码，如 biz:request、sys:file、ai:tool-log
     * @return DataScope 实例；NONE 表示拒绝全部业务数据
     */
    DataScope resolve(String resourceCode);
}
