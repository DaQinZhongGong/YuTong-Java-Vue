package com.yutong.ai.chat.ws;

import com.yutong.auth.AuthAdapter;
import com.yutong.auth.AuthContext;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScopeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * 移动端 AI 对话 WebSocket 握手。
 * 浏览器/小程序无法自定义 WS Header，因此允许 query {@code Authorization=Bearer ...}。
 * 身份仍走 {@link AuthAdapter#current()}，与 HTTP 滤器同一套上下文。
 */
@Component
public class AiChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "aiChatUserId";
    public static final String ATTR_TENANT_ID = "aiChatTenantId";
    public static final String ATTR_USERNAME = "aiChatUsername";
    public static final String ATTR_DEPT_ID = "aiChatDeptId";
    public static final String ATTR_DEPT_PATH = "aiChatDeptPath";
    public static final String ATTR_DATA_SCOPE = "aiChatDataScope";

    private static final Logger log = LoggerFactory.getLogger(AiChatHandshakeInterceptor.class);

    private final AuthAdapter authAdapter;

    public AiChatHandshakeInterceptor(AuthAdapter authAdapter) {
        this.authAdapter = authAdapter;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        ServletRequestAttributes servletAttrs = null;
        if (request instanceof ServletServerHttpRequest servletRequest) {
            servletAttrs = new ServletRequestAttributes(servletRequest.getServletRequest());
            RequestContextHolder.setRequestAttributes(servletAttrs);
        }
        try {
            AuthContext ctx = authAdapter.current();
            if (ctx == null || ctx.userId() == null || ctx.userId().isBlank()) {
                log.warn("AI Chat WS 握手拒绝: 未识别身份 remote={}", request.getRemoteAddress());
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(ATTR_USER_ID, ctx.userId());
            attributes.put(ATTR_TENANT_ID, ctx.tenantId());
            attributes.put(ATTR_USERNAME, ctx.username());
            attributes.put(ATTR_DEPT_ID, ctx.deptId());
            attributes.put(ATTR_DEPT_PATH, ctx.deptPath());
            attributes.put(ATTR_DATA_SCOPE, DataScopeType.of(ctx.dataScopeType()));
            CurrentUserContext.set(ctx.userId(), ctx.tenantId(), ctx.username(),
                    ctx.deptId(), ctx.deptPath(), DataScopeType.of(ctx.dataScopeType()));
            return true;
        } catch (Exception ex) {
            log.warn("AI Chat WS 握手失败: {}", ex.getMessage());
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        } finally {
            if (servletAttrs != null) {
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // no-op
    }
}
