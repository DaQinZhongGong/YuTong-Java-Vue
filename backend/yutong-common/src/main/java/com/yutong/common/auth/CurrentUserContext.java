package com.yutong.common.auth;

/**
 * 当前用户上下文 (ThreadLocal)。由 AuthAdapter 在请求入口填充。
 * 设计来源: 98-后端实现蓝图 AuthContext 与租户解析、67-数据权限与审计日志详设
 * yutong-infra 的 MetaObjectHandler 通过本类读取 tenantId/userId，避免循环依赖。
 * GA2-02 扩展: 新增 DEPT_ID/DEPT_PATH/DATA_SCOPE_TYPE ThreadLocal，支持 DataScope 透传。
 */
public final class CurrentUserContext {

    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> DEPT_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> DEPT_PATH = new ThreadLocal<>();
    private static final ThreadLocal<DataScopeType> DATA_SCOPE_TYPE = new ThreadLocal<>();

    private CurrentUserContext() {}

    /** 基础上下文设置（向后兼容）。 */
    public static void set(String userId, String tenantId, String username) {
        USER_ID.set(userId);
        TENANT_ID.set(tenantId);
        USERNAME.set(username);
    }

    /** 扩展上下文设置（带 dept 和 dataScopeType，GA2-02 起使用）。 */
    public static void set(String userId, String tenantId, String username,
                            String deptId, String deptPath, DataScopeType dataScopeType) {
        USER_ID.set(userId);
        TENANT_ID.set(tenantId);
        USERNAME.set(username);
        DEPT_ID.set(deptId);
        DEPT_PATH.set(deptPath);
        DATA_SCOPE_TYPE.set(dataScopeType);
    }

    public static String getUserId() { return USER_ID.get(); }
    public static String getTenantId() { return TENANT_ID.get(); }
    public static String getUsername() { return USERNAME.get(); }
    public static String getDeptId() { return DEPT_ID.get(); }
    public static String getDeptPath() { return DEPT_PATH.get(); }
    public static DataScopeType getDataScopeType() { return DATA_SCOPE_TYPE.get(); }

    public static void clear() {
        USER_ID.remove();
        TENANT_ID.remove();
        USERNAME.remove();
        DEPT_ID.remove();
        DEPT_PATH.remove();
        DATA_SCOPE_TYPE.remove();
    }
}
