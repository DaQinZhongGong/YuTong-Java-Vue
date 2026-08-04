package com.yutong.common.constant;

/**
 * 平台通用常量。
 * 设计来源: 08-API契约设计、product-baseline.yaml
 */
public final class YutongConstants {

    private YutongConstants() {}

    /** API 路径前缀 */
    public static final String API_PREFIX = "/api/v1";

    /** 默认租户 (local/test) */
    public static final String DEFAULT_TENANT_ID = "default";

    /** 通用状态 */
    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 逻辑删除 */
    public static final boolean NOT_DELETED = false;

    /** 数据权限类型 */
    public static final String DATA_SCOPE_ALL = "ALL";
    public static final String DATA_SCOPE_DEPT = "DEPT";
    public static final String DATA_SCOPE_DEPT_AND_CHILD = "DEPT_AND_CHILD";
    public static final String DATA_SCOPE_SELF = "SELF";
    public static final String DATA_SCOPE_CUSTOM = "CUSTOM";

    /** 请求头 */
    public static final String HEADER_TENANT = "X-Tenant-Id";
    public static final String HEADER_IDEMPOTENCY = "Idempotency-Key";
}
