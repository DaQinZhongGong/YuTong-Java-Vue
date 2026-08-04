package com.yutong.common.exception;

import com.yutong.common.errorcode.ErrorCode;

/** 业务冲突异常，映射 409。 */
public class BusinessConflictException extends BusinessException {
    public BusinessConflictException(String message) {
        super(ErrorCode.SYS_BUSINESS_CONFLICT, message);
    }
}
