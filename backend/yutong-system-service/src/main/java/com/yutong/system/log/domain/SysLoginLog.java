package com.yutong.system.log.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 登录审计日志。设计来源: 67-数据权限与审计日志详设 sys_login_log
 *
 * <p>记录登录、退出、登录失败、Token 刷新、Token 失效等会话事件，
 * 不得混写为普通业务操作日志。GA2-L175 落地建表 (V002) 后的 Java 层接入。
 *
 * <p>字段对齐 V002__init_system_tables.sql 第 234-258 行:
 * <ul>
 *   <li>login_type: PASSWORD/OIDC/LDAP/CAS/MOCK/TOKEN_REFRESH/LOGOUT</li>
 *   <li>login_result: SUCCESS/FAILED/DENIED/EXPIRED</li>
 *   <li>fail_reason: 失败原因摘要，禁止写明真实密码</li>
 *   <li>device_type: WEB/MOBILE/API/UNKNOWN</li>
 * </ul>
 *
 * <p>{@code loginTime} / {@code logoutTime} 使用 {@link OffsetDateTime} 对齐 DDL 的 timestamptz 类型。
 */
@Getter
@Setter
@TableName("sys_login_log")
public class SysLoginLog extends BaseEntity {

    /** 登录或会话动作类型: PASSWORD/OIDC/LDAP/CAS/MOCK/TOKEN_REFRESH/LOGOUT */
    private String loginType;

    /** 结果: SUCCESS/FAILED/DENIED/EXPIRED */
    private String loginResult;

    /** 失败原因摘要，禁止写明真实密码 (varchar 256) */
    private String failReason;

    /** 成功时用户 ID，失败时可为空 (varchar 64) */
    private String userId;

    /** 登录账号或外部主体摘要 (varchar 128) */
    private String username;

    /** Token 摘要或 jti (varchar 128) */
    private String tokenId;

    /** 设备类型: WEB/MOBILE/API/UNKNOWN (varchar 32) */
    private String deviceType;

    /** 客户端 IP (varchar 64) */
    private String ip;

    /** User-Agent 摘要 (varchar 512) */
    private String userAgent;

    /** 可选地域摘要 (varchar 128) */
    private String location;

    /** 链路 ID (varchar 64, NOT NULL) */
    private String traceId;

    /** 发生时间 (timestamptz, NOT NULL) */
    private OffsetDateTime loginTime;

    /** 退出时间，退出事件可回填 (timestamptz) */
    private OffsetDateTime logoutTime;
}
