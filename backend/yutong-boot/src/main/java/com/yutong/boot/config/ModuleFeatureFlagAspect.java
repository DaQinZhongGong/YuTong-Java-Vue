package com.yutong.boot.config;

import com.yutong.api.facade.LicenseService;
import com.yutong.api.facade.ModuleFeatureFlag;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.license.service.LocalLicenseService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * {@code @ModuleFeatureFlag} 注解的 AOP 切面 (声明式 License 门禁)。
 *
 * <p>设计来源: 70-商业授权与版本能力裁剪详设「模块授权行为矩阵」、
 * 98-后端实现蓝图与代码骨架详设 (FeatureFlag 切面)。
 *
 * <p>GA2-L171 落地: 关闭 DEV-L164-004 偏差 (ModuleFeatureFlag 未实现)。
 *
 * <p>工作流程:
 * <ol>
 *   <li>方法执行前读取 {@code @ModuleFeatureFlag(value)} 得到 moduleCode</li>
 *   <li>调用 {@link LicenseService#hasModule(String)} 双重校验 (License 授权 && yutong.modules 开关)</li>
 *   <li>未授权时写授权审计日志 ({@link LocalLicenseService#writeAudit})，
 *       抛 {@code BusinessException(LIC_MODULE_DENIED)}</li>
 * </ol>
 *
 * <p>支持方法级与类级注解; 方法级优先于类级。
 * 切面仅对 Spring Bean 的外部调用生效 (同类内部调用不触发 AOP，需注意)。
 *
 * <p>与 {@link com.yutong.system.license.interceptor.LicenseInterceptor} 的关系:
 * LicenseInterceptor 基于 URL 路径粗粒度拦截 (强制边界)，ModuleFeatureFlag 基于注解细粒度门禁 (声明式)。
 * 两者互补: 路径拦截覆盖所有 API，注解门禁用于同一路径下不同方法属于不同模块的精细控制场景。
 *
 * <p>@Profile("!cloud"): boot 模式下 LocalLicenseService 可用; cloud 模式 (v1.1+)
 * 由 FeignLicenseService 接管，审计写入策略由 License Server 处理。
 */
@Aspect
@Component
@Profile("!cloud")
public class ModuleFeatureFlagAspect {

    private static final Logger log = LoggerFactory.getLogger(ModuleFeatureFlagAspect.class);

    private final LicenseService licenseService;
    private final LocalLicenseService localLicenseService;

    public ModuleFeatureFlagAspect(LicenseService licenseService, LocalLicenseService localLicenseService) {
        this.licenseService = licenseService;
        this.localLicenseService = localLicenseService;
    }

    /**
     * 拦截方法级 {@code @ModuleFeatureFlag}。
     * 执行前校验模块授权，未授权抛 BusinessException(LIC_MODULE_DENIED)。
     */
    @Around("@annotation(com.yutong.api.facade.ModuleFeatureFlag)")
    public Object checkMethodModule(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        ModuleFeatureFlag annotation = method.getAnnotation(ModuleFeatureFlag.class);
        if (annotation != null) {
            checkModule(annotation.value(), method.getName());
        }
        return joinPoint.proceed();
    }

    /**
     * 拦截类级 {@code @ModuleFeatureFlag} (整个 Bean 的所有方法均需该模块授权)。
     * 若方法本身也有注解，方法级优先 (已由 checkMethodModule 处理，此处跳避免重复校验)。
     */
    @Around("@within(com.yutong.api.facade.ModuleFeatureFlag)")
    public Object checkTypeModule(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        if (method.getAnnotation(ModuleFeatureFlag.class) != null) {
            return joinPoint.proceed();
        }
        Class<?> targetClass = joinPoint.getTarget().getClass();
        ModuleFeatureFlag typeAnnotation = targetClass.getAnnotation(ModuleFeatureFlag.class);
        if (typeAnnotation != null) {
            checkModule(typeAnnotation.value(), method.getName());
        }
        return joinPoint.proceed();
    }

    /**
     * 校验模块授权。未授权时写审计并抛异常。
     *
     * @param moduleCode 模块编码
     * @param methodName 被拦截方法名 (用于审计详情)
     */
    private void checkModule(String moduleCode, String methodName) {
        boolean authorized;
        try {
            authorized = licenseService.hasModule(moduleCode);
        } catch (Exception e) {
            log.warn("ModuleFeatureFlag 校验异常 moduleCode={} method={} - {}",
                    moduleCode, methodName, e.getMessage());
            writeDeniedAudit(moduleCode, "LIC-500001",
                    "{\"reason\":\"license_check_error\",\"method\":\"" + escape(methodName) + "\"}");
            throw new BusinessException(ErrorCode.LIC_INTERNAL_ERROR,
                    "授权校验异常: moduleCode=" + moduleCode);
        }

        if (!authorized) {
            String detail = String.format(
                    "{\"moduleCode\":\"%s\",\"method\":\"%s\",\"source\":\"feature_flag\"}",
                    moduleCode, escape(methodName));
            writeDeniedAudit(moduleCode, ErrorCode.LIC_MODULE_DENIED.code(), detail);
            log.warn("ModuleFeatureFlag 模块未授权 moduleCode={} method={} traceId={}",
                    moduleCode, methodName, TraceContext.getTraceId());
            throw new BusinessException(ErrorCode.LIC_MODULE_DENIED,
                    "模块未授权: " + moduleCode + " (当前版本未授权此能力，请联系管理员)");
        }
    }

    private void writeDeniedAudit(String moduleCode, String errorCode, String detailJson) {
        try {
            // action=DENY 对齐 sys_license_audit_log.ck_sys_license_audit_action 约束
            localLicenseService.writeAudit("DENY", moduleCode, null, "DENIED", errorCode, detailJson);
        } catch (Exception ignored) {
            // 审计写入失败不阻断拦截流程
        }
    }

    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
