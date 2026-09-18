package com.yutong.common.encrypt;

/**
 * 加密器 SPI — 业务方实现此接口提供自定义加密算法
 * 设计来源: platform-common-encrypt + ADR 0004 P2-A
 *
 * 落点:21-安全合规详设「字段级加解密」
 *
 * 内置默认实现:
 *  - AesGcmEncryptor (AES-256-GCM, 认证加密, 推荐)
 *
 * 自定义示例:
 *   @Component("sm4Encryptor")
 *   public class Sm4Encryptor implements Encryptor {
 *       public String encrypt(String plain, String purpose) { ... }
 *       public String decrypt(String cipher, String purpose) { ... }
 *   }
 *
 *   @EncryptField(algorithm = EncryptAlgorithm.SM4_CBC, purpose = "id_card")
 *   private String idCardNo;
 *   // 业务代码: Sm4Encryptor sm4 = ...; sm4.encrypt(plain, "id_card");
 */
public interface Encryptor {

    /**
     * 加密明文
     * @param plain 明文 (UTF-8), 可能为 null
     * @param purpose 业务用途, 用于 key 派生, 同字段同 purpose 必须一致
     * @return 密文 (Base64 编码), null 入参返回 null
     * @throws EncryptException 加密失败
     */
    String encrypt(String plain, String purpose);

    /**
     * 解密密文
     * @param cipher 密文 (Base64 编码), 可能为 null
     * @param purpose 业务用途, 必须与加密时一致
     * @return 明文, null 入参返回 null
     * @throws EncryptException 解密失败 (如篡改 / 密钥错误)
     */
    String decrypt(String cipher, String purpose);

    /** 此加密器支持的算法 */
    EncryptAlgorithm algorithm();
}
