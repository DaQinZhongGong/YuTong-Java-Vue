package com.yutong.system.auth.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * OAuth2/SaaS 客户端。设计来源: platform-ai sys_client + 08-API 契约设计
 * <p>
 * 用于第三方应用接入: 每个 client 有独立 clientId/clientSecret,
 * 通过 client_credentials 模式颁发 access_token。
 * 状态: ENABLE / DISABLE
 */
@Getter
@Setter
@TableName("sys_client")
public class SysClient extends BaseEntity {

    public static final String STATUS_ENABLE = "ENABLE";
    public static final String STATUS_DISABLE = "DISABLE";

    /** 客户端 ID (公开) */
    @TableField("client_id")
    private String clientId;

    /** 客户端密钥 (BCrypt 存储) */
    @TableField("client_secret")
    private String clientSecret;

    /** 客户端名称 */
    @TableField("client_name")
    private String clientName;

    /** 授权类型: client_credentials / authorization_code / password / refresh_token (逗号分隔) */
    @TableField("grant_types")
    private String grantTypes;

    /** 设备类型: pc / mobile / miniapp */
    @TableField("device_type")
    private String deviceType;

    /** 有效时间 (秒) */
    @TableField("access_token_ttl")
    private Integer accessTokenTtl;

    /** refresh_token 有效时间 (秒) */
    @TableField("refresh_token_ttl")
    private Integer refreshTokenTtl;

    /** 重定向 URI (多个用逗号分隔) */
    @TableField("redirect_uris")
    private String redirectUris;

    /** 状态: ENABLE / DISABLE */
    private String status;
}
