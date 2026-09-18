package com.yutong.common.encrypt;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * API 传输层加密工具 — AES-256-GCM。
 * 落点: 业界同类实现 @ApiEncrypt + ADR 0005 P3。
 *
 * <p>格式: Base64(iv(12 bytes) + ciphertext + tag(16 bytes))
 * 密钥来源: MasterKeyProvider (与字段级加密共用主密钥, 但 domain separation 不同)。
 */
public final class ApiCrypto {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128; // bits

    private ApiCrypto() {}

    /**
     * 加密明文 → Base64(iv + ciphertext + tag)。
     */
    public static String encrypt(String plaintext, byte[] key) {
        if (plaintext == null || plaintext.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(deriveKey(key), "AES");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // 拼接 iv + ciphertext
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new EncryptException("API 加密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解密 Base64(iv + ciphertext + tag) → 明文。
     */
    public static String decrypt(String encrypted, byte[] key) {
        if (encrypted == null || encrypted.isEmpty()) {
            return "";
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            if (combined.length < IV_LENGTH + 1) {
                throw new EncryptException("密文格式错误: 长度不足");
            }

            byte[] iv = new byte[IV_LENGTH];
            byte[] ciphertext = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            System.arraycopy(combined, IV_LENGTH, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(deriveKey(key), "AES");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (EncryptException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptException("API 解密失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从主密钥派生 API 传输密钥 (domain separation)。
     */
    private static byte[] deriveKey(byte[] masterKey) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update("yutong:api-transport:".getBytes(StandardCharsets.UTF_8));
            digest.update(masterKey);
            return digest.digest();
        } catch (Exception e) {
            throw new EncryptException("密钥派生失败: " + e.getMessage(), e);
        }
    }
}
