package com.yutong.common.exception;

import com.yutong.common.errorcode.ErrorCode;

/**
 * 资源不存在异常，映射 404。
 * 默认使用 SYS-404001 系统级 404 错误码；业务实体不存在时可传入对应业务错误码
 * （如 BIZ-404001 申请单/BIZ-404002 客户/BIZ-404003 商品），对齐 errors.yaml 注册表。
 */
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(ErrorCode.SYS_NOT_FOUND, message);
    }
    public ResourceNotFoundException() {
        super(ErrorCode.SYS_NOT_FOUND);
    }
    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode);
    }
}
