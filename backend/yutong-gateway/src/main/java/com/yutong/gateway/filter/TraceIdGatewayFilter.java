package com.yutong.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * TraceId 全局过滤器：
 * <ul>
 *     <li>若请求头没有 X-Trace-Id，网关生成一个 UUID 注入到请求头和响应头</li>
 *     <li>若已有则原样透传到响应头</li>
 * </ul>
 */
@Slf4j
@Component
public class TraceIdGatewayFilter implements GlobalFilter, Ordered {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String traceId = request.getHeaders().getFirst(TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
            log.debug("Generate traceId: {}", traceId);
        }

        final String finalTraceId = traceId;
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        return chain.filter(mutatedExchange)
                .then(Mono.fromRunnable(() -> {
                    ServerHttpResponse response = mutatedExchange.getResponse();
                    if (response.getHeaders().getFirst(TRACE_ID_HEADER) == null) {
                        response.getHeaders().set(TRACE_ID_HEADER, finalTraceId);
                    }
                }));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
