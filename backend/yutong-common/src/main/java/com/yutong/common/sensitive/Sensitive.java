package com.yutong.common.sensitive;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字段级脱敏注解 — 标记敏感字段,序列化时自动遮罩
 * 设计来源: platform-common-sensitive @Sensitive + ADR 0004 P2-A
 *
 * 落点:67-数据权限与审计日志详设「敏感数据脱敏」+ 21-安全合规
 *
 * 用法 (手动调用, 最简单):
 *   @Sensitive(strategy = SensitiveStrategy.MOBILE)
 *   private String phone;
 *
 *   public void maskPhone(UserVO vo) {
 *       vo.setPhone(SensitiveHandlers.mask(vo.getPhone(), SensitiveStrategy.MOBILE));
 *   }
 *
 * 用法 (批量处理, 推荐):
 *   UserVO vo = ...;
 *   SensitiveManager.mask(vo);  // 反射扫描 @Sensitive 字段并遮罩
 *
 * 用法 (Jackson 自动):
 *   在 ObjectMapper 上注册 SensitiveModule, 序列化时自动遮罩
 *   (实现见 yutong-common SensitiveJacksonModule)
 *
 * WCAG 2.1: 仅展示层遮罩, 原始数据保留; UI hover/click 可申请查看完整值
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Sensitive {
    /** 脱敏策略, 默认 MOBILE */
    SensitiveStrategy strategy() default SensitiveStrategy.MOBILE;

    /** CUSTOM 策略专用: 自定义脱敏服务 bean name */
    String service() default "";
}
