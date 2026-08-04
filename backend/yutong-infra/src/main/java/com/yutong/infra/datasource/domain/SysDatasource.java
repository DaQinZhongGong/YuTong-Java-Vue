package com.yutong.infra.datasource.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 数据源元数据。设计来源: 46-多数据源与数据集设计 sys_datasource。
 * <p>
 * GA2-46 v1.5 落地能力:
 * <ul>
 *   <li>主库/从库/外部库统一管理 (db_type: POSTGRESQL/MYSQL/ORACLE/SQLSERVER)</li>
 *   <li>密钥外置 (username_ref/password_ref 只存密钥引用, 不存储明文)</li>
 *   <li>健康状态 (UP/DOWN/UNKNOWN, 由连接测试和熔断器维护)</li>
 *   <li>配置版本 (config_version, 用于热加载原子切换)</li>
 *   <li>从库延迟阈值 (lag_threshold_ms, 超过则可切回主库)</li>
 * </ul>
 */
@Getter
@Setter
@TableName("sys_datasource")
public class SysDatasource extends BaseEntity {

    public static final String DB_POSTGRESQL = "POSTGRESQL";
    public static final String DB_MYSQL = "MYSQL";
    public static final String DB_ORACLE = "ORACLE";
    public static final String DB_SQLSERVER = "SQLSERVER";

    public static final String HEALTH_UP = "UP";
    public static final String HEALTH_DOWN = "DOWN";
    public static final String HEALTH_UNKNOWN = "UNKNOWN";

    public static final String CODE_PRIMARY = "primary";
    public static final String CODE_REPORT_RO = "report_ro";

    /** 唯一编码, 如 primary / report_ro */
    private String datasourceCode;

    private String datasourceName;

    /** POSTGRESQL / MYSQL / ORACLE / SQLSERVER */
    private String dbType;

    /** JDBC 连接串 (密钥外置, jdbc_url 可存储但 username/password 只存密钥引用) */
    private String jdbcUrl;

    /** 用户名密钥引用 (如 ENV:DB_REPORT_USER 或 env:SPRING_DATASOURCE_USERNAME) */
    private String usernameRef;

    /** 密码密钥引用 (如 ENV:DB_REPORT_PASSWORD 或 env:SPRING_DATASOURCE_PASSWORD) */
    private String passwordRef;

    /** 连接池参数 (jsonb 序列化为字符串): {maximumPoolSize, minimumIdle, connectionTimeout, ...} */
    private String poolConfig;

    /** 是否只读 */
    private Boolean readOnly;

    /** 是否启用 */
    private Boolean enabled;

    /** 运行时健康状态: UP/DOWN/UNKNOWN */
    private String healthStatus;

    /** 最后检查时间 */
    private OffsetDateTime lastCheckTime;

    /** 最后错误信息 (不泄露数据库地址/用户名/密码/完整驱动异常) */
    private String lastErrorMessage;

    /** 配置版本号, 每次修改自增, 用于热加载原子切换 */
    private Integer configVersion;

    /** 启用时间 */
    private OffsetDateTime enabledTime;

    /** 从库延迟阈值 (毫秒), 超过则可切回主库 */
    private Integer lagThresholdMs;

    private String description;
}
