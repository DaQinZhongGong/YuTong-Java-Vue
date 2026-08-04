package com.yutong.system.realtime.service;

import com.yutong.auth.AuthAdapter;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * WebSocket 握手拦截器。设计来源: 44-实时通信与消息推送设计 line 75-87
 *
 * <p>职责:
 * <ul>
 *   <li>从 HTTP 握手请求头/参数解析身份（X-Mock-User 模式 / Sa-Token Authorization 模式）</li>
 *   <li>将 userId/tenantId 写入 session attributes，供后续 AUTH 帧使用</li>
 *   <li>拒绝未鉴权连接（realtime.push.enabled=true 时仍要求握手鉴权）</li>
 * </ul>
 *
 * <p>注意: 44 号文档 line 75 明确"生产环境禁止把 token 放入 URL Query"。本实现优先使用请求头，
 * query token 仅在 dev profile 下作为兜底（local/test 演示用），生产环境通过反代剥离 query。
 */
public class RealtimeHandshakeInterceptor implements HandshakeInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RealtimeHandshakeInterceptor.class);

    private final AuthAdapter authAdapter;

    public RealtimeHandshakeInterceptor(AuthAdapter authAdapter) {
        this.authAdapter = authAdapter;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // 从请求头解析身份。MockAuthAdapter.resolveMockUserType() 读取 X-Mock-User，
        // 真实模式 AuthAdapter 应从 Authorization: Bearer {token} 解析 Sa-Token。
        String userId = CurrentUserContext.getUserId();
        String tenantId = CurrentUserContext.getTenantId();
        if (userId == null || userId.isBlank()) {
            // 兜底: 检查 query token（仅 dev profile 推荐，生产环境应禁用）
            String queryToken = extractQueryToken(request);
            if (queryToken != null) {
                // 第一版 Mock 模式下不支持 token→user 反查，统一拒绝
                log.warn("WebSocket 握手拒绝: query token 模式未启用 (请使用 X-Mock-User 或 Authorization 头) remote={}",
                        request.getRemoteAddress());
            } else {
                log.warn("WebSocket 握手拒绝: 未识别到身份上下文 remote={}", request.getRemoteAddress());
            }
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }
        attributes.put(RealtimeWebSocketHandler.ATTR_USER_ID, userId);
        attributes.put(RealtimeWebSocketHandler.ATTR_TENANT_ID, tenantId);
        log.info("WebSocket 握手成功 userId={} tenantId={} remote={}",
                userId, tenantId, request.getRemoteAddress());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }

    /** 提取 query token。仅 dev 兜底用，生产环境应通过反代剥离 query。 */
    private String extractQueryToken(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null) return null;
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq > 0 && "token".equals(kv.substring(0, eq))) {
                return kv.substring(eq + 1);
            }
        }
        return null;
    }
}
