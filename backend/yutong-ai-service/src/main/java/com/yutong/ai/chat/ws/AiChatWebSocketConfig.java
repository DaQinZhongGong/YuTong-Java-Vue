package com.yutong.ai.chat.ws;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 注册 /ws/chat，与系统实时通道 /ws/v1 互不干扰。
 */
@Configuration
@EnableWebSocket
public class AiChatWebSocketConfig {

    private final AiChatWebSocketHandler handler;
    private final AiChatHandshakeInterceptor interceptor;

    public AiChatWebSocketConfig(AiChatWebSocketHandler handler, AiChatHandshakeInterceptor interceptor) {
        this.handler = handler;
        this.interceptor = interceptor;
    }

    @Bean
    public WebSocketConfigurer aiChatWebSocketConfigurer() {
        return (WebSocketHandlerRegistry registry) -> registry
                .addHandler(handler, "/ws/chat")
                .addInterceptors(interceptor)
                .setAllowedOrigins("*");
    }
}
