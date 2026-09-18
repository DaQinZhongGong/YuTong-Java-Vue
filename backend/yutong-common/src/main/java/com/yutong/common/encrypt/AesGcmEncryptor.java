package com.yutong.common.encrypt;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * AES-256-GCM 加密器 — 默认推荐实现
 * 设计来源: platform-common-encrypt + ADR 0004 P2-A
 *
 * 落点:21-安全合规详设「字段级加解密」
 *
 * 工作原理:
 *   - master key 32 字节, 通过 MasterKeyProvider 注入
 *   - 派生 key = HMAC-SHA256(master, purpose)[0:32] (domain separation)
 *   - 每次 encrypt 生成 12 字节随机 IV
 *   - 输出: Base64(IV(12) + CipherText(N) + AuthTag(16))
 *   - GCM 自带认证 (auth tag 16 字节), 防篡改
 *
 * 安全性:
 *   - 12 字节 IV 由 SecureRandom 生成, 无重用风险
 *   - 派生 key 避免 master key 直接用于业务数据
 *   - 16 字节 auth tag 检测密文篡改
 *
 * 性能:
 *   - 单次加密 ~100us (含 key 派生)
 *   - 派生 key 缓存 (purpose -> key), 同一 purpose 后续免派生
 */
public class AesGcmEncryptor implements Encryptor {

    /** AES-256 key 长度 32 字节 */
    private static final int KEY_LENGTH = 32;
    /** GCM 推荐 IV 长度 12 字节 */
    private static final int IV_LENGTH = 12;
    /** GCM auth tag 长度 128 位 = 16 字节 */
    private static final int TAG_LENGTH_BITS = 128;

    private final byte[] masterKey;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SecretKeySpec> keyCache = new ConcurrentHashMap<>();

    public AesGcmEncryptor(byte[] masterKey) {
        if (masterKey == null || masterKey.length != KEY_LENGTH) {
            throw new EncryptException(
                    "AES-256 master key 必须为 " + KEY_LENGTH + " 字节, 实际: " +
                            (masterKey == null ? "null" : masterKey.length));
        }
        this.masterKey = masterKey.clone();
    }

    @Override
    public String encrypt(String plain, String purpose) {
        if (plain == null) return null;
        if (plain.isEmpty()) return "";
        try {
            SecretKeySpec key = derivedKey(purpose);
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plain.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            // 输出格式: IV(12) + CipherText(含 16 字节 auth tag)
            ByteBuffer buf = ByteBuffer.allocate(IV_LENGTH + cipherText.length);
            buf.put(iv).put(cipherText);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new EncryptException("AES-GCM 加密失败 (purpose=" + purpose + ")", e);
        }
    }

    @Override
    public String decrypt(String cipher, String purpose) {
        if (cipher == null) return null;
        if (cipher.isEmpty()) return "";
        try {
            byte[] raw = Base64.getDecoder().decode(cipher);
            if (raw.length < IV_LENGTH + 16) {
                throw new EncryptException("密文长度异常: " + raw.length);
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(raw, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[raw.length - IV_LENGTH];
            System.arraycopy(raw, IV_LENGTH, cipherText, 0, cipherText.length);
            SecretKeySpec key = derivedKey(purpose);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] plain = c.doFinal(cipherText);
            return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
        } catch (EncryptException e) {
            throw e;
        } catch (Exception e) {
            throw new EncryptException("AES-GCM 解密失败 (purpose=" + purpose + ", 可能是篡改/密钥错误)", e);
        }
    }

    @Override
    public EncryptAlgorithm algorithm() {
        return EncryptAlgorithm.AES_256_GCM;
    }

    /**
     * 派生 key: HMAC-SHA256(masterKey, "yt-encrypt:" + purpose)
     * 缓存派生结果, 避免重复 HMAC
     */
    private SecretKeySpec derivedKey(String purpose) {
        String p = (purpose == null || purpose.isBlank()) ? "_default" : purpose;
        return keyCache.computeIfAbsent(p, k -> {
            try {
                javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                mac.init(new SecretKeySpec(masterKey, "HmacSHA256"));
                byte[] derived = mac.doFinal(("yt-encrypt:" + k).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                return new SecretKeySpec(derived, "AES");
            } catch (Exception e) {
                throw new EncryptException("派生 key 失败 (purpose=" + k + ")", e);
            }
        });
    }

    /** 清空 key 缓存 (master key 轮换时调用) */
    public void clearKeyCache() {
        keyCache.clear();
    }
}
