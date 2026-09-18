package com.yutong.ai.rag.loader;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;

/**
 * 文档抽取异常 — 继承 BusinessException 以便 GlobalExceptionHandler 按 ErrorCode 自动映射 httpStatus。
 * 生产级错误透传 ErrorCode，保留 cause 供日志排查。
 */
public class DocumentExtractException extends BusinessException {

    public DocumentExtractException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DocumentExtractException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
