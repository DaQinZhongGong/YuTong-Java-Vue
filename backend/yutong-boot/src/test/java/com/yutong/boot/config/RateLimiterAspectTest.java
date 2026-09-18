package com.yutong.boot.config;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.ratelimit.RateLimiter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimiterAspect} 单元测试 (Mockito, 无 Spring 容器、无真实 Redis)。
 * 设计来源: ADR 0004 P2-A (common-ratelimiter)。
 *
 * 覆盖:
 * - 窗口内首次请求透传
 * - 超限抛 SYS-429001
 * - 无 Redis Bean 时降级透传
 * - Redis 异常时失败开放
 * - 键格式命名空间
 */
class RateLimiterAspectTest {

    private StringRedisTemplate redisTemplate;
    private RateLimiterAspect aspect;
    private ProceedingJoinPoint joinPoint;
    private MethodSignature signature;

    /** 桩方法载体: 拿到带注解的真实 Method */
    static class StubController {
        @RateLimiter(keyPrefix = "ai:chat", permits = 2, windowSeconds = 60)
        public String chat() {
            return "ok";
        }

        @RateLimiter(keyPrefix = "drama:compose", permits = 1, windowSeconds = 300,
                keyStrategy = RateLimiter.KeyStrategy.METHOD)
        public String submit() {
            return "ok";
        }

        public String plain() {
            return "ok";
        }
    }

    /** JDK 代理形态: 接口方法无注解, 实现类方法有注解 */
    interface ChatApi {
        String chat();
    }

    static class ChatApiImpl implements ChatApi {
        @RateLimiter(keyPrefix = "ai:chat", permits = 2, windowSeconds = 60)
        @Override
        public String chat() {
            return "ok";
        }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redisTemplate);
        aspect = new RateLimiterAspect(provider);

        joinPoint = mock(ProceedingJoinPoint.class);
        signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
    }

    private void stubMethod(String name) throws Throwable {
        Method method = StubController.class.getMethod(name);
        when(signature.getMethod()).thenReturn(method);
        // proceed() 声明 throws Throwable, doReturn 链式调用仍需 throws Throwable
        org.mockito.Mockito.doReturn("ok").when(joinPoint).proceed();
    }

    @SuppressWarnings("unchecked")
    private void stubCount(Long count) {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), (Object[]) any()))
                .thenReturn(count);
    }

    @Test
    @DisplayName("窗口内首次请求透传")
    void firstRequestPasses() throws Throwable {
        stubMethod("chat");
        stubCount(1L);

        assertEquals("ok", aspect.around(joinPoint));
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("超限抛 429")
    void overLimitThrows429() throws Throwable {
        stubMethod("chat");
        stubCount(3L);

        BusinessException e = assertThrows(BusinessException.class, () -> aspect.around(joinPoint));
        assertEquals(ErrorCode.SYS_RATE_LIMITED, e.errorCode());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("计数等于上限时仍透传 (边界)")
    void atLimitPasses() throws Throwable {
        stubMethod("chat");
        stubCount(2L);

        assertEquals("ok", aspect.around(joinPoint));
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("无 Redis Bean 时降级透传")
    @SuppressWarnings("unchecked")
    void noRedisDegrades() throws Throwable {
        ObjectProvider<StringRedisTemplate> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);
        RateLimiterAspect degraded = new RateLimiterAspect(empty);
        stubMethod("chat");

        assertEquals("ok", degraded.around(joinPoint));
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("Redis 异常时失败开放")
    void redisErrorFailOpen() throws Throwable {
        stubMethod("chat");
        when(redisTemplate.execute(any(RedisScript.class), anyList(), (Object[]) any()))
                .thenThrow(new RuntimeException("connection refused"));

        assertEquals("ok", aspect.around(joinPoint));
        verify(joinPoint).proceed();
    }

    @Test
    @DisplayName("键格式: 命名空间+前缀+方法")
    void keyFormat() throws Exception {
        Method method = StubController.class.getMethod("submit");
        RateLimiter annotation = method.getAnnotation(RateLimiter.class);

        String key = aspect.buildKey(annotation, method);

        assertTrue(key.startsWith("yutong:ratelimit:drama:compose:"));
        assertTrue(key.endsWith("StubController#submit"));
    }

    @Test
    @DisplayName("Lua 脚本做 INCR+首击过期")
    void luaScriptShape() {
        assertTrue(RateLimiterAspect.LUA_SCRIPT.contains("INCR"));
        assertTrue(RateLimiterAspect.LUA_SCRIPT.contains("PEXPIRE"));
        // 占位符检查: execute 调用传 keys + 超时毫秒
        assertTrue(RateLimiterAspect.LUA_SCRIPT.contains("KEYS[1]"));
    }

    @Test
    @DisplayName("无注解方法直接透传 (不碰 Redis)")
    void plainMethodPasses() throws Throwable {
        stubMethod("plain");

        assertEquals("ok", aspect.around(joinPoint));
        verify(joinPoint).proceed();
        verify(redisTemplate, never()).execute(any(RedisScript.class), anyList(), (Object[]) any());
    }

    @Test
    @DisplayName("JDK 代理形态: 回退实现类方法注解并限流")
    void jdkProxyFallback() throws Throwable {
        Method interfaceMethod = ChatApi.class.getMethod("chat");
        when(signature.getMethod()).thenReturn(interfaceMethod);
        when(joinPoint.getTarget()).thenReturn(new ChatApiImpl());
        org.mockito.Mockito.doReturn("ok").when(joinPoint).proceed();
        stubCount(5L);

        BusinessException e = assertThrows(BusinessException.class, () -> aspect.around(joinPoint));
        assertEquals(ErrorCode.SYS_RATE_LIMITED, e.errorCode());
    }

    @Test
    @DisplayName("execute 传参: 单 key + 窗口毫秒")
    void executeArgs() throws Throwable {
        stubMethod("submit");
        stubCount(1L);

        aspect.around(joinPoint);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), (Object[]) any());
    }
}
