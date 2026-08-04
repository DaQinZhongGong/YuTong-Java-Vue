package com.yutong.common.exception;

import com.yutong.common.errorcode.ErrorCode;

/**
 * 业务异常基类。所有可控业务错误抛出 BusinessException 或其子类。
 * 设计来源: 09-Java后端设计、48-错误码注册表与API契约详设
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String customMessage;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.code());
        this.errorCode = errorCode;
        this.customMessage = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.customMessage = message;
    }

    public BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.customMessage = message;
    }

    public ErrorCode errorCode() { return errorCode; }
    public String customMessage() { return customMessage; }
}
