package com.yutong.system.log.auditable;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计日志注解。标注在 Controller 或 Service 方法上，由 {@code AuditableAspect} 拦截后
 * 自动写入 {@code sys_operation_log}。
 *
 * <p>设计来源: 67-数据权限与审计日志详设 (审计策略章节)。
 * <p>GA2-09-1: 首批接入申请单 create/update/submit/approve/reject/withdraw/archive。
 *
 * <p>用法示例:
 * <pre>
 * &#64;Auditable(operationType = "APPROVE", module = "sample", bizType = "biz_request",
 *           bizIdExpr = "#id", content = "审核通过申请单")
 * public Result&lt;BizRequest&gt; approve(&#64;PathVariable String id, &#64;RequestBody ActionRequest body) { ... }
 * </pre>
 *
 * <p>{@code bizIdExpr} 使用 SpEL 表达式从方法参数中提取业务 ID。
 * 不指定或解析为 null 时不写入 biz_id（如新建场景，bizId 在返回结果中生成）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** 操作类型枚举，对齐 67 号文档 operation_type 字段。 */
    String operationType();

    /**
     * 模块: system/sample/lowcode/ai/report/workflow/security。
     * 对齐 67 号文档 module 字段。
     */
    String module();

    /**
     * 业务类型，如 biz_request/customer/product。
     * 默认空字符串，由调用方显式指定以确保审计分类清晰。
     */
    String bizType() default "";

    /**
     * 业务 ID 的 SpEL 表达式，如 "#id"、"#request.id"。
     * 不指定或解析为 null 时 biz_id 留空（如 CREATE 场景）。
     */
    String bizIdExpr() default "";

    /**
     * 操作摘要。会作为 content 写入审计日志。
     * 不指定时使用 operationType 默认摘要。
     */
    String content() default "";

    /**
     * 是否记录方法返回值作为 after_json 快照。
     * 默认 false 以避免大对象序列化开销；关键写操作可显式开启。
     */
    boolean recordResult() default false;
}
