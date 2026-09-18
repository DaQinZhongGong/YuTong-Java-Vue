package com.yutong.ai.harness.approval;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 规范哈希工具：SHA-256 hex + 常时比较。
 */
public final class CanonicalHashes {

    private CanonicalHashes() {
    }

    /** UTF-8 SHA-256，输出小写 hex。null 输入按空串处理。 */
    public static String sha256Hex(String input) {
        byte[] data = (input == null ? "" : input).getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            return toHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256；若缺失则失败关闭
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 常时字符串比较，避免时序侧信道。任一为 null 时仅双方都为 null 才相等。 */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(left, right);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
