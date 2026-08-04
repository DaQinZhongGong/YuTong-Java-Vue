package com.yutong.common.exception;

import com.yutong.common.errorcode.ErrorCode;

/** 权限不足异常，映射 403 AUTH-403001。 */
public class PermissionDeniedException extends BusinessException {
    public PermissionDeniedException(String message) {
        super(ErrorCode.AUTH_PERMISSION_DENIED, message);
    }
    public PermissionDeniedException() {
        super(ErrorCode.AUTH_PERMISSION_DENIED);
    }
}
