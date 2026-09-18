package com.yutong.common.sensitive;

/**
 * 脱敏服务 SPI — 业务方实现此接口提供自定义脱敏
 * 设计来源: platform-common-sensitive SensitiveService + ADR 0004 P2-A
 *
 * 落点:67-数据权限与审计日志详设「敏感数据脱敏」
 *
 * 用法:
 *   @Component("myPhoneMasker")
 *   public class MyPhoneMasker implements SensitiveService {
 *       public String mask(String value) { return value; }
 *   }
 *
 *   @Sensitive(strategy = SensitiveStrategy.CUSTOM, service = "myPhoneMasker")
 *   private String customField;
 *
 * 内置默认实现 (7 种策略, 静态方法, 不需注册 bean):
 *   - SensitiveHandlers 内置 7 个静态 mask 方法
 *   - SensitiveManager 自动按 strategy 分发, 无需注册
 */
public interface SensitiveService {

    /**
     * 脱敏遮罩
     * @param value 原始值 (可能为 null)
     * @return 遮罩后值; null 入参返回 null
     */
    String mask(String value);
}
