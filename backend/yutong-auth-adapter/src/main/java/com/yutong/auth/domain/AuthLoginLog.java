package com.yutong.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 登录日志实体。设计来源: 32-企业级权限与租户接入方案 auth_login_log
 * 
 * <p>记录所有登录、登出、失败事件。不得混写为普通业务操作日志。
 * 
 * <p>login_result 常量:
 * <ul>
 *   <li>SUCCESS - 成功</li>
 *   <li>FAILED - 失败</li>
 *   <li>LOCKED - 锁定</li>
 * </ul>
 */
@Getter
@Setter
@TableName("auth_login_log")
public class AuthLoginLog extends BaseEntity {

    /** 登录结果常量 */
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_LOCKED = "LOCKED";

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 认证方式：OIDC/LDAP_AD/CAS/MOCK/EMERGENCY_ADMIN */
    private String authMethod;

    /** 登录结果：SUCCESS/FAILED/LOCKED */
    private String loginResult;

    /** 客户端 IP 地址 */
    private String ipAddress;

    /** User-Agent */
    private String userAgent;

    /** 会话 ID */
    private String sessionId;

    /** 链路追踪 ID */
    private String traceId;

    /** 失败原因 */
    private String failureReason;
}
