package com.yutong.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Refresh Token 家族实体。设计来源: 32-企业级权限与租户接入方案 auth_refresh_token_family
 * 
 * <p>跟踪 refresh token 轮换和重放检测。同一会话的所有 refresh token 共享 family_id。
 * 每次刷新都签发新 token pair 并撤销旧 refresh token；检测到旧 token 重用时撤销整个 token family。
 */
@Getter
@Setter
@TableName("auth_refresh_token_family")
public class AuthRefreshTokenFamily extends BaseEntity {

    /** token 家族 ID，同一会话的所有 refresh token 共享 */
    private String familyId;

    /** 会话 ID */
    private String sessionId;

    /** 主体 ID（用户 ID） */
    private String subjectId;

    /** 当前 JWT ID */
    private String currentJti;

    /** 设备指纹哈希 */
    private String deviceHash;

    /** 签发时间 */
    private OffsetDateTime issuedAt;

    /** 过期时间 */
    private OffsetDateTime expiresAt;

    /** 最近轮换时间 */
    private OffsetDateTime rotatedAt;

    /** 是否已撤销 */
    private Boolean revoked;

    /** 撤销原因 */
    private String revokeReason;
}
