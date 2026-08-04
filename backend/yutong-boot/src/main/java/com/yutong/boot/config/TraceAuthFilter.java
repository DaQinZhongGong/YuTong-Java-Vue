package com.yutong.boot.config;

import com.yutong.auth.AuthAdapter;
import com.yutong.auth.AuthContext;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.trace.TraceContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Trace 与认证上下文过滤器。
 * 设计来源: 62-可观测性指标日志链路详设、98-后端实现蓝图 AuthContext、67-数据权限与审计日志详设
 * 每个请求: 生成/透传 traceId，填充 CurrentUserContext（含 DataScope 透传，GA2-02 扩展）
 *
 * GA2-02 修复: 提前设置 RequestContextHolder，使 MockAuthAdapter 能在 highest precedence 过滤器中
 * 通过 RequestContextHolder 读取 X-Mock-User 请求头（否则 RequestContextFilter 尚未运行，header 读不到）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceAuthFilter extends OncePerRequestFilter {

    private final AuthAdapter authAdapter;

    public TraceAuthFilter(AuthAdapter authAdapter) {
        this.authAdapter = authAdapter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 0. 提前设置 RequestContextHolder，供 MockAuthAdapter 读取请求头
        //    （本过滤器 HIGHEST_PRECEDENCE 早于 RequestContextFilter，不提前设置则 resolveMockUserType 拿不到 header）
        ServletRequestAttributes servletAttrs = new ServletRequestAttributes(request);
        RequestContextHolder.setRequestAttributes(servletAttrs);

        // 1. traceId 透传
        String traceId = request.getHeader(TraceContext.header());
        TraceContext.setTraceId(traceId);
        response.setHeader(TraceContext.header(), TraceContext.getTraceId());

        // 2. 填充当前用户上下文 (Mock 模式直接使用 MockAuthAdapter)
        // GA2-02 扩展: 透传 deptId/deptPath/dataScopeType，支持 Repository 层读取 DataScope
        try {
            AuthContext ctx = authAdapter.current();
            if (ctx != null) {
                CurrentUserContext.set(
                        ctx.userId(), ctx.tenantId(), ctx.username(),
                        ctx.deptId(), ctx.deptPath(),
                        DataScopeType.of(ctx.dataScopeType()));
            }
        } catch (Exception ignored) {
            // 未登录请求不填充上下文
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestContextHolder.resetRequestAttributes();
            TraceContext.clear();
            CurrentUserContext.clear();
        }
    }
}
