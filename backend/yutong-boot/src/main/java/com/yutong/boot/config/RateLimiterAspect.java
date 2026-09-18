package com.yutong.boot.config;

import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.ratelimit.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code @RateLimiter} 注解的 AOP 切面 — Redis Lua 固定窗口计数。
 *
 * <p>设计来源: ADR 0004 P2-A (common-ratelimiter)。
 *
 * <p>执行流程:
 * <ol>
 *   <li>无 StringRedisTemplate Bean (单机无 Redis) → 直接透传 (失败开放)</li>
 *   <li>按 keyStrategy 组装限流键</li>
 *   <li>Lua 原子执行 INCR + 首次 EXPIRE (固定窗口)</li>
 *   <li>计数值 &gt; permits → 抛 SYS-429001 (HTTP 429); 否则透传业务</li>
 * </ol>
 *
 * <p>Redis 调用异常 → 失败开放 + warn 日志 (限流器不成为单点故障；
 * SaToken 会话本就依赖 Redis，Redis 宕机时请求早已在鉴权层失败)。
 *
 * <p>注意: 切面仅对 Spring Bean 的外部调用生效 (同类内部调用不触发 AOP)。
 */
@Aspect
@Component
public class RateLimiterAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterAspect.class);

    /**
     * 固定窗口 Lua: 计数 +1；count==1 时设置窗口过期。返回当前计数值。
     */
    static final String LUA_SCRIPT =
            "local c = redis.call('INCR', KEYS[1]); " +
            "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; " +
            "return c;";

    private static final String KEY_NAMESPACE = "yutong:ratelimit:";

    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final AtomicBoolean degradedWarned = new AtomicBoolean(false);

    public RateLimiterAspect(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
    }

    @Around("@annotation(com.yutong.common.ratelimit.RateLimiter)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = resolveMethod(joinPoint, signature.getMethod());
        RateLimiter annotation = method.getAnnotation(RateLimiter.class);
        if (annotation == null) {
            // JDK 代理下 getMethod() 可能返回无注解的接口方法: 失败开放, 直接透传
            return joinPoint.proceed();
        }

        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            warnOnce("Redis 不可用，限流降级为透传");
            return joinPoint.proceed();
        }

        String key = buildKey(annotation, method);
        long count;
        try {
            Long result = redisTemplate.execute(
                    new DefaultRedisScript<>(LUA_SCRIPT, Long.class),
                    List.of(key),
                    String.valueOf(TimeUnit.SECONDS.toMillis(Math.max(1, annotation.windowSeconds()))));
            count = result == null ? 0 : result;
        } catch (Exception e) {
            // 失败开放：不断业务，仅告警
            log.warn("限流 Redis 调用失败，透传 key={} - {}", key, e.getMessage());
            return joinPoint.proceed();
        }

        if (count > Math.max(1, annotation.permits())) {
            String message = annotation.message().isBlank()
                    ? "请求过于频繁，请稍后重试"
                    : annotation.message();
            throw new BusinessException(ErrorCode.SYS_RATE_LIMITED, message);
        }
        return joinPoint.proceed();
    }

    /**
     * 组装限流键: yutong:ratelimit:{prefix}:{维度}:{类名#方法名}。
     */
    String buildKey(RateLimiter annotation, Method method) {
        String dimension;
        switch (annotation.keyStrategy()) {
            case IP:
                dimension = clientIp();
                break;
            case METHOD:
                dimension = "global";
                break;
            case USER:
            default:
                dimension = currentUserId();
                if (dimension == null || dimension.isBlank()) {
                    dimension = "ip:" + clientIp();
                }
                break;
        }
        String prefix = annotation.keyPrefix().isBlank() ? "default" : annotation.keyPrefix().trim();
        return KEY_NAMESPACE + prefix + ":" + dimension + ":"
                + method.getDeclaringClass().getSimpleName() + "#" + method.getName();
    }

    private String currentUserId() {
        try {
            return CurrentUserContext.getUserId();
        } catch (Exception e) {
            return null;
        }
    }

    private String clientIp() {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return "unknown";
            HttpServletRequest request = attrs.getRequest();
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                return (comma < 0 ? forwarded : forwarded.substring(0, comma)).trim();
            }
            String addr = request.getRemoteAddr();
            return addr == null || addr.isBlank() ? "unknown" : addr;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 解析实际执行方法: JDK 动态代理下 signature 给的是接口方法 (注解在实现类上),
     * 此时回退到 target 实现类同名方法; 找不到则返回原方法 (调用方判空透传)。
     */
    private Method resolveMethod(ProceedingJoinPoint joinPoint, Method method) {
        if (method.getAnnotation(RateLimiter.class) != null) {
            return method;
        }
        try {
            Object target = joinPoint.getTarget();
            if (target != null) {
                Method impl = target.getClass().getMethod(method.getName(), method.getParameterTypes());
                if (impl.getAnnotation(RateLimiter.class) != null) {
                    return impl;
                }
            }
        } catch (Exception e) {
            log.debug("rate limiter method resolve fallback", e);
        }
        return method;
    }

    private void warnOnce(String message) {
        if (degradedWarned.compareAndSet(false, true)) {
            log.warn(message);
        }
    }
}
