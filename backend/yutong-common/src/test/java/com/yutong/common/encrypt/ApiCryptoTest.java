package com.yutong.common.encrypt;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ApiCrypto AES-256-GCM 加解密测试。
 */
class ApiCryptoTest {

    private final byte[] key = "test-master-key-32-bytes-long!!".getBytes(StandardCharsets.UTF_8);

    @Test
    void encrypt_decrypt_roundtrip() {
        String plaintext = "Hello, YuTong API 加密测试!";
        String encrypted = ApiCrypto.encrypt(plaintext, key);
        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);
        String decrypted = ApiCrypto.decrypt(encrypted, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_empty_returnsEmpty() {
        assertEquals("", ApiCrypto.encrypt("", key));
        assertEquals("", ApiCrypto.encrypt(null, key));
    }

    @Test
    void decrypt_empty_returnsEmpty() {
        assertEquals("", ApiCrypto.decrypt("", key));
        assertEquals("", ApiCrypto.decrypt(null, key));
    }

    @Test
    void encrypt_differentIv_eachTime() {
        String plaintext = "same input";
        String enc1 = ApiCrypto.encrypt(plaintext, key);
        String enc2 = ApiCrypto.encrypt(plaintext, key);
        // IV 随机, 每次密文不同
        assertNotEquals(enc1, enc2);
        // 但都能正确解密
        assertEquals(plaintext, ApiCrypto.decrypt(enc1, key));
        assertEquals(plaintext, ApiCrypto.decrypt(enc2, key));
    }

    @Test
    void decrypt_wrongKey_throws() {
        String encrypted = ApiCrypto.encrypt("secret", key);
        byte[] wrongKey = "wrong-key-32-bytes-long!!!!!!!!".getBytes(StandardCharsets.UTF_8);
        assertThrows(EncryptException.class, () -> ApiCrypto.decrypt(encrypted, wrongKey));
    }

    @Test
    void decrypt_tampered_throws() {
        String encrypted = ApiCrypto.encrypt("secret", key);
        // 篡改密文
        String tampered = encrypted.substring(0, 5) + "XXXX" + encrypted.substring(9);
        assertThrows(EncryptException.class, () -> ApiCrypto.decrypt(tampered, key));
    }

    @Test
    void decrypt_invalidBase64_throws() {
        assertThrows(EncryptException.class, () -> ApiCrypto.decrypt("not-valid-base64!!!", key));
    }

    @Test
    void encrypt_unicodeContent() {
        String plaintext = "中文内容 🔤 Emoji 测试";
        String encrypted = ApiCrypto.encrypt(plaintext, key);
        String decrypted = ApiCrypto.decrypt(encrypted, key);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_longContent() {
        String plaintext = "x".repeat(10000);
        String encrypted = ApiCrypto.encrypt(plaintext, key);
        String decrypted = ApiCrypto.decrypt(encrypted, key);
        assertEquals(plaintext, decrypted);
    }
}
