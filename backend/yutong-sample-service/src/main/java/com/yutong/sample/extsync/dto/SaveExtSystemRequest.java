package com.yutong.sample.extsync.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建/更新外部系统请求。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 */
@Data
public class SaveExtSystemRequest {

    @NotBlank
    @Size(max = 64)
    private String systemCode;

    @NotBlank
    @Size(max = 128)
    private String systemName;

    @Size(max = 512)
    private String description;

    @NotBlank
    @Size(max = 512)
    private String endpoint;

    /** 鉴权方式: NONE / HMAC_SHA256 / API_KEY / BEARER_TOKEN */
    @NotBlank
    @Size(max = 32)
    private String authType;

    /** 鉴权凭据 JSON (例如 {"accessKey":"...","secretKey":"..."}) */
    private String credentials;

    /** 连接超时 (秒), 默认 5 */
    private Integer connectTimeout;

    /** 读取超时 (秒), 默认 15 */
    private Integer readTimeout;

    /** 最大重试次数, 默认 3 */
    private Integer maxRetryCount;

    /** 重试退避基数 (毫秒), 默认 1000 */
    private Integer retryBackoffMs;

    /** 状态: ACTIVE / DISABLED */
    private String status;
}
