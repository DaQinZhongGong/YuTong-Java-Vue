package com.yutong.system.license.config;

import com.yutong.api.facade.LicenseService;
import com.yutong.system.license.interceptor.LicenseInterceptor;
import com.yutong.system.license.service.LocalLicenseService;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 商业授权拦截器 Spring MVC 注册。设计来源: 70-商业授权与版本能力裁剪详设「模块授权行为矩阵」、
 * 98-后端实现蓝图与代码骨架详设 (WebMvcConfigurer 装配规范)。
 *
 * <p>将 {@link LicenseInterceptor} 注册到 Spring MVC 拦截器链，
 * 拦截 6 个商业模块的 API 路径 (路径模式列表见 {@link LicenseInterceptor#INTERCEPT_PATH_PATTERNS})。
 *
 * <p>注册顺序: License 拦截器在 TraceAuthFilter (HIGHEST_PRECEDENCE Servlet Filter) 之后执行，
 * 此时 CurrentUserContext 已填充，审计日志可记录 userIdHash/tenantIdHash。
 *
 * <p>GA2-L171 落地: 关闭 DEV-L164-004 偏差（6 模块 API 拦截器尚未实现）。
 */
@Configuration
@Profile("!cloud")
public class LicenseWebMvcConfig implements WebMvcConfigurer {

    private final LicenseService licenseService;
    private final LocalLicenseService localLicenseService;

    public LicenseWebMvcConfig(LicenseService licenseService, LocalLicenseService localLicenseService) {
        this.licenseService = licenseService;
        this.localLicenseService = localLicenseService;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LicenseInterceptor(licenseService, localLicenseService))
                .addPathPatterns(LicenseInterceptor.INTERCEPT_PATH_PATTERNS);
    }
}
