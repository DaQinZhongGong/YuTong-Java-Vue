package com.yutong.common.idempotency;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 幂等注解。标注在 Controller/Service 方法上，由 {@code IdempotentAspect} 拦截后
 * 走幂等处理流程 (98 号文档第 8 步流程)。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (幂等实现模板)、58-API契约设计 (Idempotency-Key 头)
 *
 * <p>用法示例:
 * <pre>
 * &#64;Idempotent(resourceType = "biz:request", action = "CREATE", ttlSeconds = 60)
 * public Result&lt;BizRequest&gt; create(&#64;RequestBody CreateRequest body) { ... }
 *
 * &#64;Idempotent(resourceType = "biz_request", resourceIdExpr = "#id", action = "APPROVE")
 * public Result&lt;BizRequest&gt; approve(&#64;PathVariable String id, &#64;RequestBody ActionRequest body) { ... }
 * </pre>
 *
 * <p>幂等键来源: HTTP 请求头 {@code Idempotency-Key} (客户端生成 ULID/UUID)。
 * 若未携带则不启用幂等 (透传到业务)，避免无幂等诉求场景强制依赖客户端。
 *
 * <p>{@code resourceIdExpr} 使用 SpEL 表达式从方法参数中提取业务 ID，用于唯一键拼接。
 * CREATE 场景可省略 (resource_id 留空)；UPDATE/APPROVE 场景应指定以精准定位。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {

    /**
     * 资源类型，对齐 57 号文档 resource_type 字段。
     * 如 {@code biz:request}、{@code sys:file}、{@code biz_request}。
     */
    String resourceType();

    /**
     * 资源 ID 的 SpEL 表达式，如 {@code "#id"}、{@code "#request.id"}。
     * CREATE 场景可留空 (resource_id 为空字符串)，UPDATE/APPROVE 场景应指定。
     * 拼接到唯一键: tenant_id + resource_type + COALESCE(resource_id,'') + action + idempotency_key。
     */
    String resourceIdExpr() default "";

    /**
     * 动作类型，对齐 57 号文档 action 字段。
     * 如 {@code CREATE}、{@code UPDATE}、{@code APPROVE}、{@code REJECT}。
     */
    String action();

    /**
     * Redis 短锁与 PROCESSING 状态超时时间 (秒)。
     * 98 号文档要求 TTL 30~120 秒，默认 60 秒。
     */
    long ttlSeconds() default 60L;

    /**
     * 业务失败后是否允许同 key 重试。
     * 默认 false (失败终态，重放返回原错误)；true 时 4.4 步骤会重新抢占并执行。
     */
    boolean retryOnFailed() default false;
}
