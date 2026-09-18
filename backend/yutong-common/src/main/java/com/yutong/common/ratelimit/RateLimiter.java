package com.yutong.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 服务级限流注解。标注在 Controller/Service 方法上，由 {@code RateLimiterAspect}
 * (yutong-boot) 按固定窗口计数拦截。
 *
 * <p>设计来源: ADR 0004 P2-A 后端中间件 (common-ratelimiter)。
 * 网关层已有 Redis 漏桶限流；本注解覆盖单体 boot 直连场景
 * (GA 基线为 yutong-boot 单体启动器，网关为可选部署)。
 *
 * <p>用法示例:
 * <pre>
 * &#64;RateLimiter(permits = 30, windowSeconds = 60)
 * public Result&lt;AiChatVO&gt; chat(&#64;RequestBody AiChatRequest request) { ... }
 *
 * &#64;RateLimiter(keyPrefix = "drama:compose", permits = 5, windowSeconds = 300)
 * public Result&lt;DramaComposeJob&gt; submit(&#64;Valid &#64;RequestBody SubmitComposeRequest req) { ... }
 * </pre>
 *
 * <p>限流键: {@code yutong:ratelimit:{keyPrefix}:{userId|ip}:{method}}，
 * userId 取 CurrentUserContext (未登录时) / IP 取 X-Forwarded-For 首段或远端地址。
 * Redis 不可用时失败开放 (warn 日志)，避免限流器本身成为单点故障。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimiter {

    /**
     * 键前缀，用于区分业务 (默认 "default")。
     */
    String keyPrefix() default "default";

    /**
     * 窗口内允许次数 (默认 60)。
     */
    int permits() default 60;

    /**
     * 窗口秒数 (默认 60)。
     */
    int windowSeconds() default 60;

    /**
     * 限流维度 (默认按用户)。
     */
    KeyStrategy keyStrategy() default KeyStrategy.USER;

    /**
     * 超限提示 (默认使用错误码自带文案)。
     */
    String message() default "";

    /**
     * 限流维度。
     */
    enum KeyStrategy {
        /** 按当前用户 (未登录时退化为 IP) */
        USER,
        /** 按客户端 IP */
        IP,
        /** 按方法 (全局共享计数, 用于保护昂贵资源) */
        METHOD
    }
}
