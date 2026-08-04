package com.yutong.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.system.log.service.OperationLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@code @Auditable} 注解的 AOP 切面。
 *
 * <p>设计来源: 67-数据权限与审计日志详设 (审计策略)、98-后端实现蓝图。
 * <p>GA2-09-1: 首版实现——拦截标注 {@code @Auditable} 的方法，在方法执行后自动写入
 * {@code sys_operation_log}，记录 operationType/module/bizType/bizId/content/result。
 *
 * <p>执行流程:
 * <ol>
 *   <li>解析 SpEL 表达式 {@link Auditable#bizIdExpr()} 从方法参数提取 bizId</li>
 *   <li>执行业务方法</li>
 *   <li>成功: 调用 {@link OperationLogService#record} 写入 result=SUCCESS</li>
 *   <li>异常: 调用 {@link OperationLogService#record} 写入 result=FAILED + errorCode，并重新抛出原异常</li>
 * </ol>
 *
 * <p>容错: 审计日志写入本身由 {@link OperationLogService#record} 内部 try-catch 兜底，
 * 此处不再捕获写入异常，确保不影响业务流程。异常场景下也会先写审计日志再重抛异常。
 *
 * <p>SpEL 表达式示例:
 * <ul>
 *   <li>{@code #id} - 引用名为 id 的参数</li>
 *   <li>{@code #request.id} - 引用 request 参数的 id 属性</li>
 *   <li>{@code #body.version} - 引用 body 参数的 version 属性</li>
 *   <li>{@code #result.data.id} - 引用方法返回值的 data.id 属性 (CREATE 场景 id 在返回值中)</li>
 * </ul>
 *
 * <p>注意: 切面仅对 Spring Bean 的外部调用生效（同类内部调用不触发 AOP）。
 *
 * <p>DEV-L190-003 修复: 原实现构造函数内 {@code new ObjectMapper()} 未注册 JavaTimeModule，
 * 导致 afterJson 序列化含 OffsetDateTime 字段的对象（如 BaseEntity 子类 LcPage/LcEntity）
 * 时静默失败被 try-catch 吞为 null。现改为构造注入 Spring 容器中的 ObjectMapper Bean
 * （由 {@link JacksonConfig} 显式 @Bean 提供，已注册 JavaTimeModule），保证时间类型正确序列化。
 */
@Aspect
@Component
public class AuditableAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditableAspect.class);

    /** 审计结果枚举，对齐 67 号文档 result 字段。 */
    private static final String RESULT_SUCCESS = "SUCCESS";
    private static final String RESULT_FAILED = "FAILED";

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

    public AuditableAspect(OperationLogService operationLogService, ObjectMapper objectMapper) {
        this.operationLogService = operationLogService;
        // 注入 Spring 容器中的 ObjectMapper Bean（由 JacksonConfig @Bean 提供，已注册 JavaTimeModule），
        // 避免 new ObjectMapper() 不支持 OffsetDateTime 等 Java 8 时间类型导致 afterJson 序列化静默失败。
        this.objectMapper = objectMapper;
    }

    /**
     * 拦截所有标注了 {@code @Auditable} 的方法。
     * 执行后自动写入审计日志；异常时写入 FAILED 并重抛。
     */
    @Around("@annotation(com.yutong.system.log.auditable.Auditable)")
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Auditable annotation = method.getAnnotation(Auditable.class);

        Object result = null;
        Throwable thrown = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable ex) {
            thrown = ex;
            throw ex;
        } finally {
            // 无论成功失败都写审计日志；bizId 在 finally 中解析，
            // 这样既能从参数 (#id) 又能从返回值 (#result.data.id) 提取 bizId
            try {
                String bizId = resolveBizId(annotation, method, joinPoint.getArgs(), result);
                writeAuditLog(annotation, bizId, result, thrown);
            } catch (Exception e) {
                // 审计写入异常不影响业务流程
                log.error("审计切面写入失败 method={} - {}", method.getName(), e.getMessage(), e);
            }
        }
    }

    /**
     * 解析 SpEL 表达式提取 bizId。
     * 同时支持从方法参数 (如 {@code #id}、{@code #request.id}) 和返回值 (如 {@code #result.data.id}) 提取。
     * 表达式为空或解析异常时返回 null。
     *
     * @param result 方法返回值 (异常时为 null)，作为 {@code #result} 变量注入 SpEL 上下文
     */
    private String resolveBizId(Auditable annotation, Method method, Object[] args, Object result) {
        String expr = annotation.bizIdExpr();
        if (expr == null || expr.isBlank()) {
            return null;
        }
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    null, method, args, paramNameDiscoverer);
            // 注入 #result 变量，支持从返回值提取 bizId (CREATE 场景 id 在返回值中)
            context.setVariable("result", result);
            Expression expression = parser.parseExpression(expr);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("审计 bizId SpEL 解析失败 expr={} - {}", expr, e.getMessage());
            return null;
        }
    }

    /**
     * 写入审计日志。成功时 result=SUCCESS，异常时 result=FAILED + errorCode。
     */
    private void writeAuditLog(Auditable annotation, String bizId, Object result, Throwable thrown) {
        String resultStr = thrown != null ? RESULT_FAILED : RESULT_SUCCESS;
        String errorCode = thrown != null ? thrown.getClass().getSimpleName() : null;
        String content = resolveContent(annotation, thrown);
        String afterJson = null;
        if (annotation.recordResult() && result != null) {
            afterJson = serializeToJson(result);
        }
        operationLogService.record(
                annotation.operationType(),
                annotation.module(),
                annotation.bizType(),
                bizId,
                content,
                null, // beforeJson 暂不采集，后续可在 Controller 显式调用 record 扩展
                afterJson,
                resultStr,
                errorCode
        );
    }

    /**
     * 生成操作摘要。异常场景下追加异常消息便于排查。
     */
    private String resolveContent(Auditable annotation, Throwable thrown) {
        String base = annotation.content();
        if (base == null || base.isBlank()) {
            base = annotation.operationType() + " " + annotation.bizType();
        }
        if (thrown != null) {
            String msg = thrown.getMessage();
            if (msg != null && !msg.isBlank()) {
                base = base + " - FAILED: " + truncate(msg, 200);
            } else {
                base = base + " - FAILED: " + thrown.getClass().getSimpleName();
            }
        }
        return truncate(base, 500);
    }

    /**
     * 将对象序列化为 JSON 字符串。序列化失败时返回 null，
     * 因为数据库 before_json/after_json 字段为 jsonb 类型，不接受非法 JSON。
     */
    private String serializeToJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("审计 afterJson 序列化失败 type={} - {}, 设为 null", value.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /** 字符串截断保护。 */
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
