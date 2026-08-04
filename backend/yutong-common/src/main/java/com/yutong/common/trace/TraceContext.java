package com.yutong.common.trace;

/**
 * Trace 上下文工具。基于 ThreadLocal 传递 traceId。
 * 设计来源: 62-可观测性指标日志链路详设、98-后端实现蓝图
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();
    private static final String HEADER = "X-Trace-Id";

    private TraceContext() {}

    public static void setTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            traceId = generate();
        }
        TRACE_ID.set(traceId);
    }

    public static String getTraceId() {
        String tid = TRACE_ID.get();
        return tid != null ? tid : generate();
    }

    public static void clear() {
        TRACE_ID.remove();
    }

    public static String header() { return HEADER; }

    public static String generate() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }
}
