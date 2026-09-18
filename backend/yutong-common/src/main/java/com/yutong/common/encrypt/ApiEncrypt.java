package com.yutong.common.encrypt;

import java.lang.annotation.*;

/**
 * API 请求/响应体加密注解。
 * 落点: 业界同类实现 @ApiEncrypt + ADR 0005 P3。
 *
 * <p>用法: 标注在 Controller 类或方法上, 请求/响应体走 AES-256-GCM 加密传输。
 * 前端需将请求体加密为 Base64(AES-GCM(ciphertext)) 后发送, 响应同理解密。
 *
 * <p>与 {@link EncryptField} 的区别:
 * <ul>
 *   <li>{@code @EncryptField}: 数据库字段级加密 (落库前加密, 读取后解密)</li>
 *   <li>{@code @ApiEncrypt}: 传输层加密 (HTTP 请求/响应体加密, 防中间人窃听)</li>
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ApiEncrypt {

    /**
     * 是否加密请求体 (默认 true)。
     */
    boolean request() default true;

    /**
     * 是否加密响应体 (默认 true)。
     */
    boolean response() default true;
}
