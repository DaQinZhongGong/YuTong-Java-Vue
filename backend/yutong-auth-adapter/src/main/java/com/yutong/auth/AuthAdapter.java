package com.yutong.auth;

import com.yutong.common.auth.DataScope;

/**
 * 权限接入边界。local/test 使用 MockAuthAdapter，生产环境必须替换为真实 AuthAdapter。
 * 设计来源: 15-权限接入边界设计、32-企业级权限与租户接入方案、67-数据权限与审计日志详设
 * 禁止: 生产环境启用 Mock；Mock 返回 STATIC_ALL_PERMISSION。
 *
 * GA2-02 修正: getDataScope 返回类型从 String 改为 DataScope，对齐 32 号文档第 124 行契约。
 */
public interface AuthAdapter {

    /** 获取当前请求的认证上下文 (未登录返回 null)。 */
    AuthContext current();

    /** 登录并返回 token。 */
    String login(String username, String password);

    /** 注销当前会话。 */
    void logout();

    /** 校验权限码，不足抛 PermissionDeniedException。 */
    void requirePermission(String code);

    /**
     * 获取数据权限范围。设计来源: 67-数据权限与审计日志详设 第 41 行
     * 唯一数据范围来源，Repository 层负责把 DataScope 转成 SQL 条件。
     *
     * @param userId       当前用户 ID
     * @param tenantId     当前租户 ID
     * @param resourceCode 资源编码，如 biz:request、sys:file
     * @return DataScope 实例，包含 scopeType 和白名单；NONE 表示拒绝全部业务数据
     */
    DataScope getDataScope(String userId, String tenantId, String resourceCode);
}
