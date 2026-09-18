package com.yutong.common.encrypt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AES-256-GCM 加密器单元测试
 * 设计来源: ADR 0004 P2-A common-encrypt
 *
 * 覆盖:
 *  - round-trip (明文 = 解密(加密(明文)))
 *  - 同 purpose 派生同一 key (不同明文同 purpose 密文长度相近, 加密结果不同)
 *  - 不同 purpose 派生不同 key (跨 purpose 解密失败)
 *  - 篡改密文 (翻转 1 bit) → decrypt 抛错 (auth tag 校验)
 *  - 同一明文多次加密 → 密文不同 (随机 IV)
 *  - null / empty 输入
 *  - master key 长度错误抛错
 *  - Encryptors 单例 + 工具方法
 */
class AesGcmEncryptorTest {

    private AesGcmEncryptor encryptor;
    private final byte[] masterKey = new byte[32]; // 32 字节 master

    @BeforeEach
    void setUp() {
        for (int i = 0; i < masterKey.length; i++) masterKey[i] = (byte) (i + 1);
        encryptor = new AesGcmEncryptor(masterKey);
    }

    @Test
    void encrypt_thenDecrypt_returnsPlain() {
        String plain = "110101199001011234";
        String cipher = encryptor.encrypt(plain, "id_card");
        String decrypted = encryptor.decrypt(cipher, "id_card");
        assertEquals(plain, decrypted);
    }

    @Test
    void encrypt_nullInput_returnsNull() {
        assertNull(encryptor.encrypt(null, "id_card"));
        assertNull(encryptor.decrypt(null, "id_card"));
    }

    @Test
    void encrypt_emptyInput_returnsEmpty() {
        assertEquals("", encryptor.encrypt("", "id_card"));
        assertEquals("", encryptor.decrypt("", "id_card"));
    }

    @Test
    void encrypt_samePlainDifferentIv_producesDifferentCipher() {
        String plain = "13800001234";
        Set<String> ciphers = new HashSet<>();
        for (int i = 0; i < 10; i++) {
            ciphers.add(encryptor.encrypt(plain, "phone"));
        }
        assertEquals(10, ciphers.size(), "10 次加密应产生 10 个不同密文 (随机 IV)");
    }

    @Test
    void decrypt_wrongPurpose_throws() {
        String cipher = encryptor.encrypt("secret", "id_card");
        EncryptException ex = assertThrows(EncryptException.class,
                () -> encryptor.decrypt(cipher, "bank_card"));
        assertTrue(ex.getMessage().contains("purpose=bank_card"));
    }

    @Test
    void decrypt_tamperedCipher_throws() {
        String plain = "13800001234";
        String cipher = encryptor.encrypt(plain, "phone");
        // 篡改密文: 翻转最后 1 字节
        byte[] bytes = Base64.getDecoder().decode(cipher);
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        EncryptException ex = assertThrows(EncryptException.class,
                () -> encryptor.decrypt(tampered, "phone"));
        assertTrue(ex.getMessage().contains("解密失败"),
                "GCM auth tag 校验失败应抛 EncryptException");
    }

    @Test
    void decrypt_truncatedCipher_throws() {
        String cipher = encryptor.encrypt("x", "p");
        byte[] bytes = Base64.getDecoder().decode(cipher);
        // 截断到 IV+1 字节, 缺 auth tag
        String truncated = Base64.getEncoder().encodeToString(java.util.Arrays.copyOf(bytes, 13));
        assertThrows(EncryptException.class, () -> encryptor.decrypt(truncated, "p"));
    }

    @Test
    void decrypt_invalidBase64_throws() {
        assertThrows(EncryptException.class, () -> encryptor.decrypt("not-valid-base64!@#$", "p"));
    }

    @Test
    void constructor_wrongKeyLength_throws() {
        byte[] shortKey = new byte[16];
        assertThrows(EncryptException.class, () -> new AesGcmEncryptor(shortKey));
        byte[] longKey = new byte[64];
        assertThrows(EncryptException.class, () -> new AesGcmEncryptor(longKey));
    }

    @Test
    void constructor_nullKey_throws() {
        assertThrows(EncryptException.class, () -> new AesGcmEncryptor(null));
    }

    @Test
    void differentInstances_sameMasterKey_canDecryptEachOther() {
        AesGcmEncryptor other = new AesGcmEncryptor(masterKey);
        String cipher = encryptor.encrypt("data-1", "shared-purpose");
        // 不同实例但 masterKey + purpose 一致, 可互解
        assertEquals("data-1", other.decrypt(cipher, "shared-purpose"));
    }

    @Test
    void keyCache_isolatedByPurpose() {
        // 同一明文不同 purpose 产生不同密文
        String cipher1 = encryptor.encrypt("same", "purpose-A");
        String cipher2 = encryptor.encrypt("same", "purpose-B");
        assertNotEquals(cipher1, cipher2);
        // 跨 purpose 不可解
        assertThrows(EncryptException.class, () -> encryptor.decrypt(cipher1, "purpose-B"));
    }

    @Test
    void clearKeyCache_works() {
        encryptor.encrypt("a", "p");
        encryptor.clearKeyCache();
        // 清空后仍能正常加解密 (重新派生)
        String cipher = encryptor.encrypt("b", "p");
        assertEquals("b", encryptor.decrypt(cipher, "p"));
    }

    @Test
    void algorithm_returnsExpected() {
        assertEquals(EncryptAlgorithm.AES_256_GCM, encryptor.algorithm());
    }

    @Test
    void chinesePlaintext_roundTripsCorrectly() {
        String plain = "张三丰 13800001234 北京市朝阳区";
        String cipher = encryptor.encrypt(plain, "user_profile");
        String decrypted = encryptor.decrypt(cipher, "user_profile");
        assertEquals(plain, decrypted);
    }

    @Test
    void longPlaintext_roundTripsCorrectly() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000; i++) sb.append("abcdefghij");
        String plain = sb.toString();
        String cipher = encryptor.encrypt(plain, "long");
        assertEquals(plain, encryptor.decrypt(cipher, "long"));
    }

    @Test
    void encryptors_defaultInit_thenGet() {
        Encryptors.initDefault(encryptor);
        String c = Encryptors.defaultEncryptor().encrypt("hi", "p");
        assertEquals("hi", Encryptors.defaultEncryptor().decrypt(c, "p"));
    }

    @Test
    void encryptors_defaultNotInit_throws() {
        // 重新清除: 用反射清 static field
        try {
            java.lang.reflect.Field f = Encryptors.class.getDeclaredField("DEFAULT");
            f.setAccessible(true);
            f.set(null, null);
        } catch (Exception e) {
            // ignore
        }
        assertThrows(EncryptException.class, Encryptors::defaultEncryptor);
    }

    @Test
    void encryptors_registerAndGetByName() {
        AesGcmEncryptor a = new AesGcmEncryptor(masterKey);
        AesGcmEncryptor b = new AesGcmEncryptor(masterKey);
        Encryptors.register("aesA", a);
        Encryptors.register("aesB", b);
        assertEquals(a, Encryptors.get("aesA"));
        assertEquals(b, Encryptors.get("aesB"));
        assertThrows(EncryptException.class, () -> Encryptors.get("nonExist"));
    }

    @Test
    void generateDevMasterKey_returns32BytesBase64() {
        String b64 = Encryptors.generateDevMasterKey();
        byte[] key = Base64.getDecoder().decode(b64);
        assertEquals(32, key.length, "dev master key 必须 32 字节");
        // 两次生成不同
        String b64_2 = Encryptors.generateDevMasterKey();
        assertNotEquals(b64, b64_2, "随机生成, 两次应不同");
    }

    @Test
    void cipher_isNotPlaintext() {
        String plain = "13800001234";
        String cipher = encryptor.encrypt(plain, "phone");
        assertNotNull(cipher);
        assertFalse(cipher.contains(plain), "密文不应包含明文");
        // 密文应是 Base64 格式
        assertTrue(cipher.matches("^[A-Za-z0-9+/=]+$"), "密文应是 Base64 字符");
    }
}
