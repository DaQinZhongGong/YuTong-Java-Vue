package com.yutong.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 身份提供商配置实体。设计来源: 32-企业级权限与租户接入方案 auth_identity_provider
 * 
 * <p>保存 OIDC/LDAP_AD/CAS/WECHAT/DINGTALK/FEISHU 等身份提供商配置。
 * 
 * <p>provider_type 常量:
 * <ul>
 *   <li>OIDC - OpenID Connect</li>
 *   <li>LDAP_AD - LDAP/Active Directory</li>
 *   <li>CAS - Central Authentication Service</li>
 *   <li>WECHAT - 微信</li>
 *   <li>DINGTALK - 钉钉</li>
 *   <li>FEISHU - 飞书</li>
 * </ul>
 */
@Getter
@Setter
@TableName("auth_identity_provider")
public class AuthIdentityProvider extends BaseEntity {

    /** 提供商类型常量 */
    public static final String TYPE_OIDC = "OIDC";
    public static final String TYPE_LDAP_AD = "LDAP_AD";
    public static final String TYPE_CAS = "CAS";
    public static final String TYPE_WECHAT = "WECHAT";
    public static final String TYPE_DINGTALK = "DINGTALK";
    public static final String TYPE_FEISHU = "FEISHU";

    /** 状态常量 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    /** 提供商编码，租户内唯一 */
    private String providerCode;

    /** 提供商类型：OIDC/LDAP_AD/CAS/WECHAT/DINGTALK/FEISHU */
    private String providerType;

    /** OIDC 发行者 URL */
    private String issuer;

    /** OAuth2 客户端 ID */
    private String clientId;

    /** 加密后的客户端密钥 */
    private String clientSecretEncrypted;

    /** 重定向 URI */
    private String redirectUri;

    /** 请求的权限范围，空格分隔 */
    private String scope;

    /** JWKS 公钥端点 */
    private String jwksUri;

    /** 用户信息端点 */
    private String userinfoEndpoint;

    /** 授权端点 */
    private String authEndpoint;

    /** 令牌端点 */
    private String tokenEndpoint;

    /** 登出端点 */
    private String logoutEndpoint;

    /** 时钟偏差容忍秒数，默认 60 */
    private Integer clockSkewSeconds;

    /** 组映射 JSON，外部组到平台角色/权限的映射 */
    private String groupMappingJson;

    /** 角色映射 JSON */
    private String roleMappingJson;

    /** 状态：ACTIVE/INACTIVE */
    private String status;
}
