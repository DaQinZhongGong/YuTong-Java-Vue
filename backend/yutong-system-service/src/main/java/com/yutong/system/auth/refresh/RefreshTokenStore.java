package com.yutong.system.auth.refresh;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.id.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Refresh Token Redis 存储 + 轮换服务。设计来源: 21-安全合规详设 (access=15min + refresh=14d, 轮换)
 *
 * <p>refresh token 为不透明随机串 (UUID)，作为 Redis key 的一部分存储，载荷 JSON 为 value。
 * 每次成功刷新必须删除旧 token 并签发新 token (轮换)，符合 21 号文档「refresh token 每次使用必须轮换」要求。
 *
 * <p>Redis key 格式: {@code yutong:auth:refresh:{token}}
 * <p>TTL: {@code AUTH_REFRESH_TOKEN_TTL_SECONDS} (默认 1209600 = 14 天)
 *
 * <p>注意: 与 yutong-auth-adapter 中的 DB 版 RefreshTokenRotationService 不同，
 * 此服务面向 boot 单体模式，使用 Redis 存储实现轻量级轮换。
 * 两者均满足「使用即轮换」要求，DB 版额外提供 token family 重放检测，可在生产身份增强场景下启用。
 */
@Service
public class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    /** Redis key 前缀。 */
    private static final String KEY_PREFIX = "yutong:auth:refresh:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** refresh token 有效期 (秒)，默认 14 天 (1209600)。 */
    @Value("${yutong.auth.refresh-token.ttl-seconds:${AUTH_REFRESH_TOKEN_TTL_SECONDS:1209600}}")
    private long ttlSeconds;

    /** 是否启用 refresh token 轮换 (默认 true，对齐 21 号文档要求)。 */
    @Value("${yutong.auth.refresh-token.rotation-enabled:${AUTH_REFRESH_ROTATION_ENABLED:true}}")
    private boolean rotationEnabled;

    public RefreshTokenStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * 签发新的 refresh token 并存入 Redis。
     *
     * @param payload 会话上下文载荷
     * @return refresh token 字符串 (UUID)
     */
    public String issue(RefreshTokenPayload payload) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String key = KEY_PREFIX + token;
        String value = serialize(payload);
        redis.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
        log.info("Refresh token 已签发: userId={}, username={}, ttl={}s", payload.userId(), payload.username(), ttlSeconds);
        return token;
    }

    /**
     * 校验并轮换 refresh token。
     *
     * <p>校验通过后立即删除旧 token (轮换)，并签发新 refresh token 关联同一载荷。
     * 如果 refresh token 不存在或已过期，返回 null 表示需要重新登录。
     *
     * @param refreshToken 旧 refresh token
     * @return 轮换结果 (新 refresh token + 原载荷)，校验失败返回 null
     */
    public RotationResult validateAndRotate(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        String key = KEY_PREFIX + refreshToken;
        String value = redis.opsForValue().get(key);
        if (value == null) {
            log.warn("Refresh token 校验失败: 不存在或已过期");
            return null;
        }
        RefreshTokenPayload payload = deserialize(value);
        if (payload == null) {
            // 载荷反序列化失败，清理脏数据
            redis.delete(key);
            log.warn("Refresh token 载荷反序列化失败，已清理: key={}", key);
            return null;
        }
        // 轮换: 删除旧 token (无论 rotationEnabled 与否，单次使用即失效；rotationEnabled 控制是否签发新 token)
        redis.delete(key);
        if (!rotationEnabled) {
            // 未启用轮换: 仅删除旧 token，不签发新 token (调用方需提示重新登录)
            log.info("Refresh token 轮换已禁用，仅删除旧 token: userId={}", payload.userId());
            return new RotationResult(null, payload);
        }
        // 签发新 refresh token，关联原载荷
        String newToken = issue(payload);
        log.info("Refresh token 已轮换: userId={}, oldJti={}, newJti={}", payload.userId(), refreshToken, newToken);
        return new RotationResult(newToken, payload);
    }

    /**
     * 主动撤销 refresh token (登出/强制下线时调用)。
     *
     * @param refreshToken 待撤销的 refresh token
     * @return 是否撤销成功
     */
    public boolean revoke(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return false;
        }
        Boolean deleted = redis.delete(KEY_PREFIX + refreshToken);
        return Boolean.TRUE.equals(deleted);
    }

    /** 轮换结果。 */
    public record RotationResult(
            /** 新签发的 refresh token (rotationEnabled=false 时为 null) */
            String newRefreshToken,
            /** 原 refresh token 关联的载荷 */
            RefreshTokenPayload payload
    ) {
    }

    private String serialize(RefreshTokenPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Refresh token payload 序列化失败", e);
        }
    }

    private RefreshTokenPayload deserialize(String value) {
        try {
            return objectMapper.readValue(value, RefreshTokenPayload.class);
        } catch (Exception e) {
            log.warn("Refresh token payload 反序列化失败: {}", e.getMessage());
            return null;
        }
    }
}