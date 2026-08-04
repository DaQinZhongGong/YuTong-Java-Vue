package com.yutong.boot.config;

import com.yutong.common.idempotency.Idempotent;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.idempotency.dto.IdempotentRequest;
import com.yutong.system.idempotency.dto.IdempotentResult;
import com.yutong.system.idempotency.service.IdempotencyService;
import jakarta.servlet.http.HttpServletRequest;
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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * {@code @Idempotent} 注解的 AOP 切面。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (幂等实现模板)、58-API契约设计 (Idempotency-Key 头)
 *
 * <p>执行流程:
 * <ol>
 *   <li>从 HTTP 头读取 {@code Idempotency-Key}，未携带时直接透传业务 (不启用幂等)</li>
 *   <li>解析 {@link Idempotent#resourceIdExpr()} SpEL 表达式提取 resourceId</li>
 *   <li>调用 {@link IdempotencyService#computeRequestHash} 计算方法参数 hash</li>
 *   <li>构建 {@link IdempotentRequest} 调用 {@link IdempotencyService#execute}</li>
 *   <li>透传结果 (replay=true 时为重放，replay=false 时为本次执行)</li>
 * </ol>
 *
 * <p>响应类型解析: 业务方法通常返回 {@code Result<T>}，需提取 T 作为 responseType
 * 以便 IdempotencyService 反序列化 snapshot。返回类型非 Result 时直接使用方法返回类型。
 *
 * <p>注意: 切面仅对 Spring Bean 的外部调用生效 (同类内部调用不触发 AOP)。
 */
@Aspect
@Component
public class IdempotentAspect {

    private static final Logger log = LoggerFactory.getLogger(IdempotentAspect.class);

    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer paramNameDiscoverer = new DefaultParameterNameDiscoverer();

    public IdempotentAspect(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Around("@annotation(com.yutong.common.idempotency.Idempotent)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Idempotent annotation = method.getAnnotation(Idempotent.class);

        // 1. 读取 Idempotency-Key 头
        String idempotencyKey = readIdempotencyKey();
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            // 未携带 Idempotency-Key，不启用幂等，直接透传业务
            return joinPoint.proceed();
        }

        // 2. 解析 resourceId SpEL
        String resourceId = resolveResourceId(annotation, method, joinPoint.getArgs());

        // 3. 计算请求 hash (基于方法参数)
        String requestHash = IdempotencyService.computeRequestHash(joinPoint.getArgs());

        // 4. 构建 IdempotentRequest
        IdempotentRequest request = new IdempotentRequest(
                idempotencyKey,
                annotation.resourceType(),
                resourceId,
                annotation.action(),
                requestHash,
                annotation.ttlSeconds(),
                annotation.retryOnFailed()
        );

        // 5. 解析响应类型 (从 Result<T> 提取 T)
        Class<?> responseType = resolveResponseType(method);

        // 6. 执行幂等业务
        // responseType 为运行时反射得到的 Class<?>，泛型推断无法解析 T，使用 raw type 调用
        IdempotentResult<?> result = invokeExecute(request, joinPoint, responseType);

        if (log.isDebugEnabled()) {
            log.debug("幂等执行完成 key={} action={} replay={}",
                    idempotencyKey, annotation.action(), result.replay());
        }
        return result.data();
    }

    /**
     * 从当前 HTTP 请求头读取 Idempotency-Key。
     * 非 Web 场景 (异步任务) 返回 null。
     */
    private String readIdempotencyKey() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        return request.getHeader(IDEMPOTENCY_HEADER);
    }

    /**
     * 解析 resourceId SpEL 表达式。
     */
    private String resolveResourceId(Idempotent annotation, Method method, Object[] args) {
        String expr = annotation.resourceIdExpr();
        if (expr == null || expr.isBlank()) return null;
        try {
            MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                    null, method, args, paramNameDiscoverer);
            Expression expression = parser.parseExpression(expr);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.warn("幂等 resourceId SpEL 解析失败 expr={} - {}", expr, e.getMessage());
            return null;
        }
    }

    /**
     * 解析业务响应类型。从 {@code Result<T>} 提取 T 作为 responseType。
     * 非 Result 返回类型直接使用方法返回类型。
     */
    private Class<?> resolveResponseType(Method method) {
        Type returnType = method.getGenericReturnType();
        if (returnType instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType == Result.class) {
                Type[] typeArgs = pt.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> clazz) {
                    return clazz;
                }
            }
        }
        return method.getReturnType();
    }

    /**
     * TraceId 兜底 (未使用时保留)。
     */
    @SuppressWarnings("unused")
    private String currentTraceId() {
        return TraceContext.getTraceId();
    }

    /**
     * 调用 IdempotencyService.execute，运行时 responseType 是 Class<?>，
     * Java 泛型推断无法解析 T，使用 raw type 调用以绕过编译期类型检查。
     * 业务异常通过 Supplier 包装为 RuntimeException 抛出，由 GlobalExceptionHandler 兜底。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private IdempotentResult<Object> invokeExecute(IdempotentRequest request,
                                                   ProceedingJoinPoint joinPoint,
                                                   Class<?> responseType) throws Throwable {
        java.util.function.Supplier<Object> business = () -> {
            try {
                return joinPoint.proceed();
            } catch (Throwable t) {
                if (t instanceof RuntimeException re) throw re;
                throw new RuntimeException(t);
            }
        };
        Class rawClass = responseType;
        return idempotencyService.execute(request, business, rawClass);
    }
}
