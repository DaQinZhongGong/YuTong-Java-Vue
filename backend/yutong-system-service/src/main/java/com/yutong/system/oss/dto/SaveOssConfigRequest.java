package com.yutong.system.oss.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * 保存 OSS 配置请求。
 */
@Getter
@Setter
public class SaveOssConfigRequest {

    /** ID (空 = 创建, 非空 = 更新) */
    private String id;

    @NotBlank(message = "配置键不能为空")
    @Pattern(regexp = "^[a-z0-9][a-z0-9_-]{1,62}[a-z0-9]$",
            message = "配置键仅允许小写字母/数字/下划线/中划线, 3-64 位")
    private String configKey;

    @NotBlank(message = "配置名称不能为空")
    private String configName;

    @NotBlank(message = "存储类型不能为空")
    @Pattern(regexp = "^(MINIO|S3|LOCAL)$", message = "存储类型仅允许 MINIO/S3/LOCAL")
    private String storageType;

    @NotBlank(message = "端点不能为空")
    private String endpoint;

    @NotBlank(message = "访问密钥不能为空")
    private String accessKey;

    @NotBlank(message = "密钥 Secret 不能为空")
    private String secretKey;

    @NotBlank(message = "桶名不能为空")
    private String bucketName;

    private String domain;

    private String region;

    private Boolean isHttps;

    private Boolean isDefault;

    private String remark;
}
