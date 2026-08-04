package com.yutong.system.datasource.dto;

import lombok.Data;

import java.time.OffsetDateTime;

/**
 * 数据源 VO (脱敏)。GA2-46 v1.5。
 * <p>
 * 安全约束 (46 号文档 line 103):
 * <ul>
 *   <li>jdbc_url 脱敏: 只返回 host:port, 隐藏 query 参数中的密钥</li>
 *   <li>username_ref/password_ref 只返回引用本身 (如 env:VAR_NAME), 不返回解析后的真实值</li>
 *   <li>last_error_message 不泄露数据库地址/用户名/密码/完整驱动异常</li>
 * </ul>
 */
@Data
public class DatasourceVO {

    private String id;
    private String datasourceCode;
    private String datasourceName;
    private String dbType;

    /** 脱敏后的 jdbc_url: 只保留 jdbc:postgresql://host:port/db, 隐藏 user/password 参数 */
    private String jdbcUrlMasked;

    /** 密钥引用 (不返回解析后的真实值) */
    private String usernameRef;
    private String passwordRef;

    private String poolConfig;
    private Boolean readOnly;
    private Boolean enabled;

    /** 运行时健康状态: UP/DOWN/UNKNOWN */
    private String healthStatus;
    private OffsetDateTime lastCheckTime;

    /** 脱敏后的错误信息 (不泄露数据库地址/用户名/密码/完整驱动异常) */
    private String lastErrorMessage;

    private Integer configVersion;
    private OffsetDateTime enabledTime;
    private Integer lagThresholdMs;
    private String description;
    private OffsetDateTime createdTime;
    private OffsetDateTime updatedTime;
}
