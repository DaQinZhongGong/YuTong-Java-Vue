package com.yutong.common.exception;

import com.yutong.common.errorcode.ErrorCode;

/** 数据权限拒绝异常，映射 403 AUTH-403002，必须写越权审计。 */
public class DataScopeDeniedException extends BusinessException {
    public DataScopeDeniedException(String message) {
        super(ErrorCode.AUTH_DATA_SCOPE_DENIED, message);
    }
    public DataScopeDeniedException() {
        super(ErrorCode.AUTH_DATA_SCOPE_DENIED);
    }
}
