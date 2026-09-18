package com.yutong.common.encrypt;

/**
 * 加密异常 — 加解密失败时抛出
 * 设计来源: ADR 0004 P2-A common-encrypt
 *
 * 落点:21-安全合规详设
 *
 * 常见原因:
 *  - 密文篡改 (AES-GCM auth tag 校验失败)
 *  - 密钥错误 (purpose 与加密时不一致)
 *  - 密文格式错误 (非 Base64 / 长度不够)
 *  - master key 未配置 (启动时未注入)
 */
public class EncryptException extends RuntimeException {
    public EncryptException(String message) {
        super(message);
    }
    public EncryptException(String message, Throwable cause) {
        super(message, cause);
    }
}
