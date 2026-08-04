package com.yutong.system.realtime.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.auth.AuthAdapter;
import com.yutong.system.realtime.service.RealtimeHandshakeInterceptor;
import com.yutong.system.realtime.service.RealtimeWebSocketHandler;
import com.yutong.system.realtime.service.SessionRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置。设计来源: 44-实时通信与消息推送设计 line 33
 *
 * <p>注册 /ws/v1 端点（不经过 /api/v1 前缀，对齐 44 号文档 line 33/150）。
 * 仅当 yutong.realtime.push.enabled=true 时启用，关闭时整个 WebSocket 配置不加载，
 * 端侧自动回退到 REST 拉取（GA 基线无损失，44 号文档 line 7/150）。
 *
 * <p>GA2-L180: {@code @EnableConfigurationProperties(RealtimeProperties.class)} 已迁移至
 * {@link RealtimePropertiesConfiguration}，确保 {@code enabled=false} 时 RealtimeProperties 仍可注入
 * PushService 等无条件注册的 @Service Bean。
 *
 * <p>allowedOrigins: 第一版使用 "*" 适配本地开发跨域；生产环境应通过反代或显式白名单收紧。
 */
@Configuration
@EnableWebSocket
@ConditionalOnProperty(prefix = "yutong.realtime.push", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final RealtimeProperties properties;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final AuthAdapter authAdapter;

    public WebSocketConfig(RealtimeProperties properties,
                           SessionRegistry sessionRegistry,
                           ObjectMapper objectMapper,
                           AuthAdapter authAdapter) {
        this.properties = properties;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.authAdapter = authAdapter;
    }

    @Bean
    public RealtimeWebSocketHandler realtimeWebSocketHandler() {
        return new RealtimeWebSocketHandler(properties, sessionRegistry, objectMapper);
    }

    @Bean
    public RealtimeHandshakeInterceptor realtimeHandshakeInterceptor() {
        return new RealtimeHandshakeInterceptor(authAdapter);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(realtimeWebSocketHandler(), properties.getEndpoint())
                .addInterceptors(realtimeHandshakeInterceptor())
                .setAllowedOrigins("*");
        log.info("WebSocket 端点注册完成 path={} queueSize={} heartbeat={}s",
                properties.getEndpoint(), properties.getQueueSize(), properties.getHeartbeatSeconds());
    }
}
