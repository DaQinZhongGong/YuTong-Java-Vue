package com.yutong.system.realtime.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * SSE 跨实例广播配置。
 * 仅在 Redis 可用时注册监听容器; 无 Redis 时降级为仅本地广播。
 */
@Configuration
public class SseBroadcastConfig {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcastConfig.class);

    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer sseRedisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            SseBroadcastListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        listener.registerTo(container);
        log.info("[SseBroadcast] Redis listener container initialized");
        return container;
    }
}
