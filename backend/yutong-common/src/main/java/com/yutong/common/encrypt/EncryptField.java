package com.yutong.common.encrypt;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级加密注解 — 标记敏感字段需加密存储 / 脱敏展示
 * 设计来源: platform-common-encrypt @EncryptField + ADR 0004 P2-A
 *
 * 落点:21-安全合规详设「字段级加解密」
 *
 * 用法 (手动调用, 最简单):
 *   @EncryptField(algorithm = EncryptAlgorithm.AES_256_GCM, purpose = "id_card")
 *   private String idCardNo;
 *
 *   public void setIdCardNo(String plain) {
 *       this.idCardNo = Encryptors.aesGcm("id_card").encrypt(plain);
 *   }
 *   public String getIdCardNo() {
 *       return idCardNo == null ? null : Encryptors.aesGcm("id_card").decrypt(idCardNo);
 *   }
 *
 * 用法 (MyBatis 拦截器自动透明):
 *   entity 上加 @EncryptField, 装配 MyBatis 拦截器 (yutong-infra 后续批次)
 *   interceptor 自动在 setParameter 时加密 / getResult 时解密
 *
 * purpose 字段:
 *   - 用于 key 派生, 不同 purpose 派生不同密钥 (domain separation)
 *   - 同字段同 purpose 在不同环境 (dev/staging/prod) 可配置不同 master key
 *
 * WCAG:仅影响存储密文, 不影响前端展示 (前端调用解密 API 或后端响应自动解密)
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface EncryptField {
    /** 加密算法, 默认 AES-256-GCM (认证加密) */
    EncryptAlgorithm algorithm() default EncryptAlgorithm.AES_256_GCM;

    /** 业务用途 (用于 key 派生 domain separation), 如 "id_card" / "bank_card" / "phone" */
    String purpose() default "";

    /** 加密后是否仍保留原字段长度 (true 时 16 字节 IV 截断, 不推荐, 默认 false) */
    boolean truncate() default false;
}
