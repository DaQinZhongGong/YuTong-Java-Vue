package com.yutong.common.exception;

import com.yutong.common.errorcode.ErrorCode;

/**
 * 幂等冲突异常，映射 409 SYS-409003。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (幂等实现模板第 4.1 步)
 * <p>触发场景: 同一 Idempotency-Key 但 request_hash 不同——表明客户端用相同 key
 * 发送了不同的请求体，违反幂等契约，必须拒绝以避免误用。
 */
public class IdempotencyConflictException extends BusinessException {
    public IdempotencyConflictException(String message) {
        super(ErrorCode.SYS_IDEMPOTENCY_CONFLICT, message);
    }
    public IdempotencyConflictException() {
        super(ErrorCode.SYS_IDEMPOTENCY_CONFLICT);
    }
}
