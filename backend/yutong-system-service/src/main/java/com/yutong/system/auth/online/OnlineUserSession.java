package com.yutong.system.auth.online;

import java.time.OffsetDateTime;

/**
 * 在线用户会话信息。
 */
public record OnlineUserSession(
        String tokenId,
        String userId,
        String username,
        String tenantId,
        String ip,
        String userAgent,
        OffsetDateTime loginTime,
        OffsetDateTime lastActiveTime
) {
}
