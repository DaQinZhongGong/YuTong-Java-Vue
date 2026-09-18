package com.yutong.system.oss.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * OSS 配置。设计来源: V054 sys_oss_config + 业界同类实现 sys_oss_config。
 * 支持多桶 MinIO/S3/Local 配置, is_default 标记默认配置。
 */
@Getter
@Setter
@TableName("sys_oss_config")
public class SysOssConfig extends BaseEntity {

    /** 配置键 (唯一, 如 default/backup/cdn) */
    private String configKey;

    /** 配置名称 */
    private String configName;

    /** 存储类型: MINIO / S3 / LOCAL */
    private String storageType;

    /** 存储端点 */
    private String endpoint;

    /** 访问密钥 ID */
    private String accessKey;

    /** 访问密钥 Secret (敏感) */
    private String secretKey;

    /** 桶名 */
    private String bucketName;

    /** 自定义域名/CDN */
    private String domain;

    /** 区域 */
    private String region;

    /** 是否 HTTPS */
    private Boolean isHttps;

    /** 是否默认配置 */
    private Boolean isDefault;

    /** 状态: ENABLED / DISABLED */
    private String status;

    public static final String STORAGE_MINIO = "MINIO";
    public static final String STORAGE_S3 = "S3";
    public static final String STORAGE_LOCAL = "LOCAL";

    public static final String STATUS_ENABLED = "ENABLED";
    public static final String STATUS_DISABLED = "DISABLED";
}
