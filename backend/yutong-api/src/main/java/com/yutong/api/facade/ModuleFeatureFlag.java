package com.yutong.api.facade;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 模块功能开关注解 (声明式 License 门禁)。
 *
 * <p>设计来源: 70-商业授权与版本能力裁剪详设「模块授权行为矩阵」、
 * 98-后端实现蓝图与代码骨架详设 (FeatureFlag 注解)。
 *
 * <p>标注在 Controller 方法或类上，声明该方法/类属于哪个商业模块。
 * {@code ModuleFeatureFlagAspect} 在方法执行前调用
 * {@link LicenseService#hasModule(String)} 校验模块授权，
 * 未授权时抛 {@code BusinessException(LIC_MODULE_DENIED)} 并写授权审计。
 *
 * <p>使用场景: 当 LicenseInterceptor 的路径拦截不够精细时 (如同一 Controller 内
 * 部分方法属于商业模块、部分属于基础模块)，可用本注解声明式标注。
 *
 * <p>支持方法级与类级注解; 方法级优先于类级。
 * 切面仅对 Spring Bean 的外部调用生效 (同类内部调用不触发 AOP)。
 *
 * <p>GA2-L171 落地: 关闭 DEV-L164-004 偏差 (ModuleFeatureFlag 未实现)。
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ModuleFeatureFlag {

    /**
     * 模块编码。对齐 70 号文档模块授权行为矩阵:
     * lowcode / ai / report / workflow / datasource / plugin
     */
    String value();
}
