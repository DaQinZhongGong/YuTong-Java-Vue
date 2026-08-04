package com.yutong.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 跨域配置（安全加固）：
 * <ul>
 *     <li>禁止 * 通配 origin（生产环境必须显式配置）</li>
 *     <li>允许 Credentials 时 origin 必须为明确值</li>
 *     <li>暴露 X-Trace-Id 头</li>
 * </ul>
 * 设计来源: YuTong-Java-Docs/64-安全威胁模型与风控详设
 */
@Configuration
public class CorsConfig {

    @Value("${yutong.security.cors.allowed-origins:http://localhost:20010,http://localhost:5173,http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        for (String origin : allowedOrigins) {
            config.addAllowedOrigin(origin.trim());
        }
        config.addAllowedMethod("GET");
        config.addAllowedMethod("POST");
        config.addAllowedMethod("PUT");
        config.addAllowedMethod("DELETE");
        config.addAllowedMethod("PATCH");
        config.addAllowedMethod("OPTIONS");
        config.addAllowedHeader("*");
        config.setAllowCredentials(true);
        config.addExposedHeader("X-Trace-Id");
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return new CorsWebFilter(source);
    }
}
