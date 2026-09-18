package com.yutong.common.sensitive;

import java.util.function.UnaryOperator;

/**
 * 脱敏处理器 — 7 种内置策略的纯函数实现
 * 设计来源: platform-common-sensitive + ADR 0004 P2-A
 *
 * 落点:67-数据权限与审计日志详设「敏感数据脱敏」
 *
 * 规则:
 *   - MOBILE      138****1234     (前 3 后 4)
 *   - EMAIL       a***@example.com (首字母 + *** + 域名)
 *   - ID_CARD     110101********1234 (前 6 后 4)
 *   - BANK_CARD   6222 **** **** 1234 (前 4 后 4, 加空格)
 *   - CHINESE_NAME 张三 -> 张*; 张三丰 -> 张*丰 (隐藏中间 1 字)
 *   - ADDRESS     北京市朝阳区... -> 北京市朝阳区**** (前 6 字符 + ****)
 *   - FIXED_PHONE 010-12345678 -> 010-****5678 (保留前 4 后 4)
 *
 * 所有方法纯函数: 入参 null/空 -> 返回 null/空
 */
public final class SensitiveHandlers {

    /** 默认 mask 字符 */
    public static final String MASK = "****";
    /** 中文姓名 mask 字符 */
    public static final String CN_NAME_MASK = "*";

    private SensitiveHandlers() {
    }

    /**
     * 按策略分发的 mask
     */
    public static String mask(String value, SensitiveStrategy strategy) {
        if (value == null || value.isEmpty() || strategy == null) return value;
        return switch (strategy) {
            case MOBILE -> mobile(value);
            case EMAIL -> email(value);
            case ID_CARD -> idCard(value);
            case BANK_CARD -> bankCard(value);
            case CHINESE_NAME -> chineseName(value);
            case ADDRESS -> address(value);
            case FIXED_PHONE -> fixedPhone(value);
            case CUSTOM -> value; // CUSTOM 由业务方 SPI 实现, 此处不处理
        };
    }

    /**
     * 手机号: 138****1234
     * 长度 < 7 直接返回 (非合法手机号)
     */
    public static String mobile(String value) {
        if (value == null || value.isEmpty()) return value;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 7) return value;
        return digits.substring(0, 3) + MASK + digits.substring(digits.length() - 4);
    }

    /**
     * 邮箱: a***@example.com
     */
    public static String email(String value) {
        if (value == null || value.isEmpty()) return value;
        int at = value.indexOf('@');
        if (at <= 0) return value;
        return value.charAt(0) + "***" + value.substring(at);
    }

    /**
     * 身份证: 110101********1234
     * 长度 < 10 直接返回
     */
    public static String idCard(String value) {
        if (value == null || value.isEmpty()) return value;
        if (value.length() < 10) return value;
        StringBuilder sb = new StringBuilder();
        sb.append(value, 0, 6);
        for (int i = 6; i < value.length() - 4; i++) sb.append('*');
        sb.append(value, value.length() - 4, value.length());
        return sb.toString();
    }

    /**
     * 银行卡: 6222 **** **** 1234
     * 长度 < 8 直接返回
     */
    public static String bankCard(String value) {
        if (value == null || value.isEmpty()) return value;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 8) return value;
        return digits.substring(0, 4) + " **** **** " + digits.substring(digits.length() - 4);
    }

    /**
     * 中文姓名: 张三 -> 张*; 张三丰 -> 张*丰; 欧阳娜娜 -> 欧**娜
     * 规则: 隐藏中间部分, 保留首尾各 1 字 (3 字及以上)
     *       2 字: 保留首字, 末字 mask
     *       1 字: mask
     */
    public static String chineseName(String value) {
        if (value == null || value.isEmpty()) return value;
        int len = value.length();
        if (len == 1) return CN_NAME_MASK;
        if (len == 2) return value.charAt(0) + CN_NAME_MASK;
        // 3 字及以上: 保留首字, mask 中间, 保留末字
        StringBuilder sb = new StringBuilder();
        sb.append(value.charAt(0));
        for (int i = 1; i < len - 1; i++) sb.append(CN_NAME_MASK);
        sb.append(value.charAt(len - 1));
        return sb.toString();
    }

    /**
     * 地址: 北京市朝阳区... -> 北京市朝阳区****
     * 保留前 6 字符
     */
    public static String address(String value) {
        if (value == null || value.isEmpty()) return value;
        int keep = Math.min(6, value.length());
        return value.substring(0, keep) + MASK;
    }

    /**
     * 固定电话: 010-12345678 -> 010-****5678
     * 长度 < 8 直接返回
     */
    public static String fixedPhone(String value) {
        if (value == null || value.isEmpty()) return value;
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() < 8) return value;
        // 保留前 4 + 后 4, 中间用 **** 替换
        // 保留原格式 (- / 空格) 的简单实现: 仅 digits 处理
        return digits.substring(0, 4) + MASK + digits.substring(digits.length() - 4);
    }

    /**
     * 自定义 mask: 接受 lambda 函数
     * 用法: SensitiveHandlers.custom(value, s -> "***" + s.length() + "***")
     */
    public static String custom(String value, UnaryOperator<String> masker) {
        if (value == null) return null;
        if (masker == null) return value;
        return masker.apply(value);
    }
}
