package com.yutong.system.auth.refresh;

/**
 * Refresh Token 关联的会话上下文载荷。设计来源: 21-安全合规详设 (refresh token 轮换)
 *
 * <p>登录成功后签发 refresh token 时，将用户身份与原始 access token 摘要一同写入 Redis。
 * 刷新时取出该载荷用于重签 access token，并删除旧 refresh token (轮换)。
 *
 * <p>refresh token 本体只作为 Redis key，载荷明文存储用户上下文，敏感凭据不写入载荷。
 */
public record RefreshTokenPayload(
        /** 用户 ID */
        String userId,
        /** 用户名 */
        String username,
        /** 租户 ID */
        String tenantId,
        /** Mock 用户类型 (local/test profile 前端通过 X-Mock-User 头携带) */
        String mockUserType,
        /** 登录时签发的原始 access token (刷新时用于回签同类型 token) */
        String accessToken
) {
}
