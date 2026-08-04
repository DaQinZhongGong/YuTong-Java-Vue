package com.yutong.common.response;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 统一响应结构。
 * 设计来源: 08-API契约设计、09-Java后端设计、98-后端实现蓝图与代码骨架详设
 * code=0 表示成功；非 0 为错误码（与 errors.yaml 对齐）。
 */
public record Result<T>(
        String code,
        String message,
        String messageKey,
        Map<String, Object> messageArgs,
        T data,
        String traceId,
        OffsetDateTime timestamp
) {
    public static <T> Result<T> ok(T data, String traceId) {
        return new Result<>("0", "success", null, null, data, traceId, OffsetDateTime.now());
    }

    public static <T> Result<T> ok(T data) {
        return ok(data, null);
    }

    public static <T> Result<T> ok() {
        return ok(null, null);
    }

    public static <T> Result<T> fail(String code, String message, String traceId) {
        return new Result<>(code, message, null, null, null, traceId, OffsetDateTime.now());
    }

    public static <T> Result<T> fail(String code, String message, String messageKey, Map<String, Object> messageArgs, String traceId) {
        return new Result<>(code, message, messageKey, messageArgs, null, traceId, OffsetDateTime.now());
    }

    public boolean success() {
        return "0".equals(code);
    }
}
