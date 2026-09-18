package com.yutong.common.translation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 字典/用户/部门翻译注解 — Jackson 序列化时自动注入显示名
 * 设计来源: platform-common-translation @Translation + ADR 0004 P2-A
 *
 * 落点:50-设计系统 + 84-字典/用户/部门显示名自动化详设
 *
 * 用法 1(字典):
 *   private String status;              // 字典值 (如 "ENABLED")
 *   @Translation(type = "DICT", dictType = "sys_user_status", ref = "status")
 *   private String statusLabel;        // 序列化时自动填 "启用"
 *
 * 用法 2(用户):
 *   private String createBy;           // userId
 *   @Translation(type = "USER", ref = "createBy")
 *   private String createByName;       // 序列化时自动填用户名
 *
 * 用法 3(部门):
 *   private String deptId;
 *   @Translation(type = "DEPT", ref = "deptId")
 *   private String deptName;
 *
 * 规则:
 *  - ref 必填, 指向同 VO 中已存在的外键 ID 字段名
 *  - 标注字段必须可被 Jackson 序列化 (有 public getter 或 public 字段)
 *  - 多个 @Translation 可引用同一个 ref
 *  - 翻译失败 (查不到记录) 时静默写 null, 不抛错
 *
 * WCAG:仅影响序列化输出, 不影响业务逻辑; 接口契约稳定
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Translation {
    /** 翻译类型, 默认 DICT */
    TranslationType type() default TranslationType.DICT;

    /** 引用同 VO 中哪个 ID 字段 (如 "createBy" / "deptId") */
    String ref();

    /** DICT 类型专用:字典类型编码 (如 "sys_user_status") */
    String dictType() default "";

    /** CUSTOM 类型专用:自定义 translator bean name, 留空时按 type 查找 */
    String translator() default "";
}
