package com.yutong.system.auth.online;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 在线用户会话注册表 — Redis Hash 实现。
 * 落点: 业界同类实现 SysUserOnlineController + ADR 0005 P1-D。
 *
 * <p>设计:
 * <ul>
 *   <li>Redis Hash: key=yutong:online:users, field=tokenId, value=JSON 会话信息</li>
 *   <li>登录时 register(), 登出时 remove(), 请求时 touch() 刷新活跃时间</li>
 *   <li>TTL 30 分钟 (与 sa-token.timeout 15min 对齐, 留 2 倍余量防边界)</li>
 *   <li>kick() 强制下线: 删除 Redis Hash 字段 + 写入黑名单 (短 TTL 防 token 复用)</li>
 *   <li>无 Redis 时降级为空列表 (失败开放, 不阻断业务)</li>
 * </ul>
 */
@Service
public class OnlineUserService {

    private static final Logger log = LoggerFactory.getLogger(OnlineUserService.class);

    private static final String ONLINE_KEY = "yutong:online:users";
    private static final String KICK_PREFIX = "yutong:online:kicked:";
    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration KICK_TTL = Duration.ofMinutes(15);

    private final StringRedisTemplate redisTemplate;

    public OnlineUserService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 注册在线会话 (登录时调用)。
     */
    public void register(String tokenId, String userId, String username,
                         String tenantId, String ip, String userAgent) {
        try {
            String json = buildSessionJson(tokenId, userId, username, tenantId, ip, userAgent);
            redisTemplate.opsForHash().put(ONLINE_KEY, tokenId, json);
            redisTemplate.expire(ONLINE_KEY, SESSION_TTL);
            log.debug("[OnlineUser] registered token={}, user={}", tokenId, username);
        } catch (Exception e) {
            log.warn("[OnlineUser] register failed: {}", e.getMessage());
        }
    }

    /**
     * 刷新活跃时间 (认证拦截时调用)。
     */
    public void touch(String tokenId) {
        try {
            Object val = redisTemplate.opsForHash().get(ONLINE_KEY, tokenId);
            if (val == null) return;
            // 更新 lastActiveTime
            String json = val.toString().replaceAll(
                    "\"lastActiveTime\":\"[^\"]*\"",
                    "\"lastActiveTime\":\"" + OffsetDateTime.now() + "\"");
            redisTemplate.opsForHash().put(ONLINE_KEY, tokenId, json);
            redisTemplate.expire(ONLINE_KEY, SESSION_TTL);
        } catch (Exception e) {
            log.debug("[OnlineUser] touch failed: {}", e.getMessage());
        }
    }

    /**
     * 移除在线会话 (登出时调用)。
     */
    public void remove(String tokenId) {
        try {
            redisTemplate.opsForHash().delete(ONLINE_KEY, tokenId);
            log.debug("[OnlineUser] removed token={}", tokenId);
        } catch (Exception e) {
            log.warn("[OnlineUser] remove failed: {}", e.getMessage());
        }
    }

    /**
     * 强制下线 (踢人)。
     * 删除会话 + 写入黑名单 (token 在黑名单 TTL 内不可复用)。
     */
    public boolean kick(String tokenId) {
        try {
            Object val = redisTemplate.opsForHash().get(ONLINE_KEY, tokenId);
            if (val == null) return false;
            redisTemplate.opsForHash().delete(ONLINE_KEY, tokenId);
            redisTemplate.opsForValue().set(KICK_PREFIX + tokenId, "1", KICK_TTL);
            log.info("[OnlineUser] kicked token={}", tokenId);
            return true;
        } catch (Exception e) {
            log.warn("[OnlineUser] kick failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查 token 是否被踢。
     */
    public boolean isKicked(String tokenId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KICK_PREFIX + tokenId));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 列出全部在线会话。
     */
    public List<OnlineUserSession> listAll() {
        try {
            Map<Object, Object> entries = redisTemplate.opsForHash().entries(ONLINE_KEY);
            return entries.values().stream()
                    .map(v -> parseSessionJson(v.toString()))
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(OnlineUserSession::lastActiveTime,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("[OnlineUser] listAll failed: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 按用户名筛选。
     */
    public List<OnlineUserSession> listByKeyword(String keyword) {
        return listAll().stream()
                .filter(s -> keyword == null || keyword.isBlank()
                        || s.username().toLowerCase().contains(keyword.toLowerCase())
                        || s.userId().contains(keyword))
                .collect(Collectors.toList());
    }

    /**
     * 统计在线人数。
     */
    public long count() {
        try {
            Long size = redisTemplate.opsForHash().size(ONLINE_KEY);
            return size != null ? size : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    // ==================== 内部方法 ====================

    private String buildSessionJson(String tokenId, String userId, String username,
                                    String tenantId, String ip, String userAgent) {
        String now = OffsetDateTime.now().toString();
        return String.format(
                "{\"tokenId\":\"%s\",\"userId\":\"%s\",\"username\":\"%s\",\"tenantId\":\"%s\"," +
                "\"ip\":\"%s\",\"userAgent\":\"%s\",\"loginTime\":\"%s\",\"lastActiveTime\":\"%s\"}",
                escape(tokenId), escape(userId), escape(username), escape(tenantId),
                escape(ip), escape(userAgent != null ? userAgent : ""), now, now);
    }

    private OnlineUserSession parseSessionJson(String json) {
        try {
            // 轻量 JSON 解析 (避免引 Jackson 依赖到此服务)
            String tokenId = extract(json, "tokenId");
            String userId = extract(json, "userId");
            String username = extract(json, "username");
            String tenantId = extract(json, "tenantId");
            String ip = extract(json, "ip");
            String userAgent = extract(json, "userAgent");
            String loginTime = extract(json, "loginTime");
            String lastActiveTime = extract(json, "lastActiveTime");
            return new OnlineUserSession(
                    tokenId, userId, username, tenantId, ip, userAgent,
                    parseTime(loginTime), parseTime(lastActiveTime));
        } catch (Exception e) {
            return null;
        }
    }

    private String extract(String json, String field) {
        String prefix = "\"" + field + "\":\"";
        int start = json.indexOf(prefix);
        if (start < 0) return "";
        start += prefix.length();
        int end = json.indexOf("\"", start);
        return end > start ? json.substring(start, end) : "";
    }

    private OffsetDateTime parseTime(String s) {
        try {
            return OffsetDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
