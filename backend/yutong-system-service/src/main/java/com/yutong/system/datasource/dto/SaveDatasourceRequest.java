package com.yutong.system.datasource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 保存数据源请求。GA2-46 v1.5 落地。
 * <p>
 * 密钥外置约束 (46 号文档 line 103): jdbc_url 可存储, username_ref/password_ref 只存密钥引用 (如 env:VAR_NAME), 不存储明文。
 * <p>
 * 热加载约束 (46 号文档 line 74-83): 保存后需调用 test 端点验证, 通过后才会创建/替换连接池。
 */
@Data
public class SaveDatasourceRequest {

    /** 数据源编码 (唯一), 如 primary / report_ro / external_erp */
    @NotBlank(message = "datasourceCode 不能为空")
    @Size(max = 64, message = "datasourceCode 长度不能超过 64")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]*$", message = "datasourceCode 必须以字母开头, 只允许字母数字下划线")
    private String datasourceCode;

    @NotBlank(message = "datasourceName 不能为空")
    @Size(max = 128, message = "datasourceName 长度不能超过 128")
    private String datasourceName;

    /** POSTGRESQL / MYSQL / ORACLE / SQLSERVER */
    @NotBlank(message = "dbType 不能为空")
    private String dbType;

    /** JDBC 连接串 */
    @NotBlank(message = "jdbcUrl 不能为空")
    @Size(max = 512, message = "jdbcUrl 长度不能超过 512")
    private String jdbcUrl;

    /** 用户名密钥引用, 如 env:SPRING_DATASOURCE_USERNAME */
    @Size(max = 128, message = "usernameRef 长度不能超过 128")
    private String usernameRef;

    /** 密码密钥引用, 如 env:SPRING_DATASOURCE_PASSWORD */
    @Size(max = 128, message = "passwordRef 长度不能超过 128")
    private String passwordRef;

    /** 连接池参数 (jsonb 字符串): {maximumPoolSize, minimumIdle, connectionTimeout, ...} */
    private String poolConfig;

    /** 是否只读 (外部库必须为 true) */
    private Boolean readOnly;

    /** 是否启用 */
    private Boolean enabled;

    /** 从库延迟阈值 (毫秒), 超过则可切回主库 */
    private Integer lagThresholdMs;

    private String description;
}
