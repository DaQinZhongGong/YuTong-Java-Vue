package com.yutong.gateway.config;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * 安全响应头过滤器：
 * <ul>
 *     <li>X-Content-Type-Options: nosniff（防止 MIME 嗅探）</li>
 *     <li>X-Frame-Options: DENY（防止点击劫持）</li>
 *     <li>X-XSS-Protection: 1; mode=block（浏览器 XSS 过滤）</li>
 *     <li>Strict-Transport-Security: max-age=31536000; includeSubDomains（HSTS）</li>
 *     <li>Referrer-Policy: strict-origin-when-cross-origin（Referrer 策略）</li>
 * </ul>
 * 设计来源: YuTong-Java-Docs/64-安全威胁模型与风控详设
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersWebFilter implements WebFilter {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var headers = exchange.getResponse().getHeaders();
        headers.add("X-Content-Type-Options", "nosniff");
        headers.add("X-Frame-Options", "DENY");
        headers.add("X-XSS-Protection", "1; mode=block");
        headers.add("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        headers.add("Referrer-Policy", "strict-origin-when-cross-origin");
        return chain.filter(exchange);
    }
}
