package com.yutong.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

import java.security.Principal;

/**
 * 限流 KeyResolver 配置：
 * <ul>
 *     <li>优先使用 PrincipalName（登录用户）</li>
 *     <li>无 principal 时使用 remoteAddr</li>
 * </ul>
 */
@Configuration
public class RateLimiterConfig {

    @Primary
    @Bean
    public KeyResolver principalNameKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty(
                        exchange.getRequest().getRemoteAddress() != null
                                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                                : "unknown"
                )
                .flatMap(key -> Mono.justOrEmpty(key.isBlank() ? "unknown" : key));
    }
}
