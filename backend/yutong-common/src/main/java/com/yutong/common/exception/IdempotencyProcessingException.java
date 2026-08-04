package com.yutong.common.exception;

import com.yutong.common.errorcode.ErrorCode;

/**
 * 幂等处理中异常，映射 409 SYS-409002。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (幂等实现模板第 4.3 步)
 * <p>触发场景: 同一 Idempotency-Key 的请求正在处理中 (status=PROCESSING 且未超时)，
 * 客户端应稍后重试，不应直接展示为失败。
 */
public class IdempotencyProcessingException extends BusinessException {
    public IdempotencyProcessingException(String message) {
        super(ErrorCode.SYS_IDEMPOTENCY_PROCESSING, message);
    }
    public IdempotencyProcessingException() {
        super(ErrorCode.SYS_IDEMPOTENCY_PROCESSING);
    }
}
