package com.yutong.common.encrypt;

/**
 * 加密算法枚举 — @EncryptField 注解 algorithm 字段
 * 设计来源: platform-common-encrypt + ADR 0004 P2-A 中间件补强
 *
 * 落点:21-安全合规详设「字段级加解密」+ 67-数据权限与审计日志
 *
 * 选型:生产环境推荐 AES-256-GCM(认证加密,防篡改)
 *       国密场景后续可加 SM2/SM4 (本批次先做 AES 主流)
 */
public enum EncryptAlgorithm {
    /** AES-256-GCM (16 字节 IV + 16 字节 auth tag,认证加密) — 默认推荐 */
    AES_256_GCM,
    /** AES-128-CBC (16 字节 IV,无认证,仅兼容旧数据) */
    AES_128_CBC,
    /** SM4-CBC (国密对称,16 字节 IV) — 后续支持 */
    SM4_CBC
}
