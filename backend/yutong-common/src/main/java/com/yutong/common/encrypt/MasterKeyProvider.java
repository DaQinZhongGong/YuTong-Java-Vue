package com.yutong.common.encrypt;

/**
 * master key 提供方 SPI — 由业务方实现, 用于注入 master key
 * 设计来源: ADR 0004 P2-A common-encrypt
 *
 * 落点:21-安全合规详设「字段级加解密」
 *
 * 用法:
 *   @Component
 *   public class EnvMasterKeyProvider implements MasterKeyProvider {
 *       public byte[] masterKey() {
 *           String b64 = env.get("YUTONG_ENCRYPT_MASTER_KEY"); // 32 字节 base64
 *           return Base64.getDecoder().decode(b64);
 *       }
 *   }
 *
 *   @Configuration
 *   public class EncryptConfig {
 *       @Bean
 *       public Encryptor aesGcmEncryptor(MasterKeyProvider provider) {
 *           return new AesGcmEncryptor(provider.masterKey());
 *       }
 *   }
 *
 * 生产建议:
 *   - dev: yutong-common-test-master-key (硬编码, 仅本地开发)
 *   - staging/prod: 环境变量 / KMS / Vault, 严禁入仓
 */
public interface MasterKeyProvider {

    /**
     * 提供 32 字节 master key
     * 警告: 不要硬编码到代码 / 配置文件 / git
     * 推荐: 环境变量 / KMS / Spring Cloud Config + 加解密
     */
    byte[] masterKey();
}
