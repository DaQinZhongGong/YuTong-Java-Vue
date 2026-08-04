package com.yutong.sample.extsync.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 外部系统注册。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>描述一个待集成的第三方系统 (endpoint + 鉴权配置 + 重试策略)。
 * <p>鉴权方式支持: NONE / HMAC_SHA256 / API_KEY / BEARER_TOKEN。
 */
@Getter
@Setter
@TableName("ext_system")
public class ExtSystem extends BaseEntity {

    public static final String AUTH_NONE = "NONE";
    public static final String AUTH_HMAC_SHA256 = "HMAC_SHA256";
    public static final String AUTH_API_KEY = "API_KEY";
    public static final String AUTH_BEARER_TOKEN = "BEARER_TOKEN";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 系统编码 (租户内唯一) */
    private String systemCode;
    /** 系统名称 */
    private String systemName;
    /** 描述 */
    private String description;
    /** 接入端点 (基地址) */
    private String endpoint;
    /** 鉴权方式 */
    private String authType;
    /** 鉴权凭据 (JSON) */
    private String credentials;
    /** 连接超时 (秒) */
    private Integer connectTimeout;
    /** 读取超时 (秒) */
    private Integer readTimeout;
    /** 最大重试次数 */
    private Integer maxRetryCount;
    /** 重试退避基数 (毫秒) */
    private Integer retryBackoffMs;
    /** 状态: ACTIVE / DISABLED */
    private String status;
}
