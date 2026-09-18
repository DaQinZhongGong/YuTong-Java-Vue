package com.yutong.common.sensitive;

/**
 * 脱敏策略枚举 — @Sensitive 注解 strategy 字段
 * 设计来源: platform-common-sensitive + ADR 0004 P2-A 中间件补强
 *
 * 落点:67-数据权限与审计日志详设「敏感数据脱敏」+ 21-安全合规
 *
 * 内置 7 种策略, 覆盖国内常见敏感字段
 * 自定义策略: 业务方实现 SensitiveService SPI 并 register 到 SensitiveManager
 */
public enum SensitiveStrategy {
    /** 手机号: 138****1234 (前 3 后 4) */
    MOBILE,
    /** 邮箱: a***@example.com (首字母 + 域名) */
    EMAIL,
    /** 身份证: 110101********1234 (前 6 后 4) */
    ID_CARD,
    /** 银行卡: 6222 **** **** 1234 (前 4 后 4) */
    BANK_CARD,
    /** 中文姓名: 张三 -> 张*; 张三丰 -> 张*丰 (单字 1 个, 双字及以上 1 个) */
    CHINESE_NAME,
    /** 地址: 北京市朝阳区... -> 北京市朝阳区**** (前 6 字符 + ****) */
    ADDRESS,
    /** 固定电话: 010-12345678 -> 010-****5678 (前 4 后 4) */
    FIXED_PHONE,
    /** 自定义: 由业务方 SPI 实现 */
    CUSTOM
}
