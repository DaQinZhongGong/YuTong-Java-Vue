package com.yutong.boot.config;

import com.yutong.auth.AuthAdapter;
import com.yutong.auth.RequiresPermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@code @RequiresPermission} 注解的 AOP 切面。
 *
 * <p>设计来源: 64-安全威胁模型 TC-SEC-AUTH-001、98-后端实现蓝图权限注解与菜单权限。
 * <p>GA2-03-6: 修复安全缺口——此前 {@code @RequiresPermission} 仅作为文档标记，运行时不拦截。
 * 本切面在方法执行前调用 {@link AuthAdapter#requirePermission(String)} 校验权限码，
 * 不足时抛 {@code PermissionDeniedException} (403 AUTH-403001)。
 *
 * <p>支持方法级与类级注解；方法级优先于类级。
 * 切面仅对 Spring Bean 的外部调用生效（同类内部调用不触发 AOP，需注意）。
 */
@Aspect
@Component
public class RequiresPermissionAspect {

    private final AuthAdapter authAdapter;

    public RequiresPermissionAspect(AuthAdapter authAdapter) {
        this.authAdapter = authAdapter;
    }

    /**
     * 拦截所有标注了 {@code @RequiresPermission} 的方法。
     * 执行前校验权限码，校验失败抛 PermissionDeniedException。
     */
    @Around("@annotation(com.yutong.auth.RequiresPermission)")
    public Object checkMethodPermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation != null) {
            authAdapter.requirePermission(annotation.value());
        }
        return joinPoint.proceed();
    }

    /**
     * 拦截类级 {@code @RequiresPermission}（整个 Bean 的所有方法均需该权限）。
     * 若方法本身也有注解，方法级优先（已由上面的切面处理，此处仅作为类级兜底）。
     */
    @Around("@within(com.yutong.auth.RequiresPermission)")
    public Object checkTypePermission(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        // 方法级注解已由 checkMethodPermission 处理，此处跳避免重复校验
        if (method.getAnnotation(RequiresPermission.class) != null) {
            return joinPoint.proceed();
        }
        Class<?> targetClass = joinPoint.getTarget().getClass();
        RequiresPermission typeAnnotation = targetClass.getAnnotation(RequiresPermission.class);
        if (typeAnnotation != null) {
            authAdapter.requirePermission(typeAnnotation.value());
        }
        return joinPoint.proceed();
    }
}
