package com.yutong.auth.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 认证模式校验器。设计来源: 32-企业级权限与租户接入方案
 * 
 * <p>启动时校验 YUTONG_AUTH_MODE，staging/prod 环境禁止 MOCK。
 * 商业部署必须在部署清单中固定 identity.mode，同一访问入口只能有一个主模式。
 * 允许值为 OIDC、SSO、LDAP_AD、CUSTOMER_IAM、LOCAL_IAM_EXTENSION；
 * MOCK 和静态全权限模式在 staging/prod 启动时必须失败。
 */
@Slf4j
@Component
public class AuthModeValidator {

    /** 允许的认证模式 */
    public static final String MODE_MOCK = "MOCK";
    public static final String MODE_OIDC = "OIDC";
    public static final String MODE_LDAP_AD = "LDAP_AD";
    public static final String MODE_CAS = "CAS";
    public static final String MODE_CUSTOMER_IAM = "CUSTOMER_IAM";
    public static final String MODE_LOCAL_IAM_EXTENSION = "LOCAL_IAM_EXTENSION";

    @Value("${yutong.auth.mode:MOCK}")
    private String authMode;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    /**
     * 应用启动后校验认证模式
     * 
     * <p>staging/prod 环境禁止 MOCK，启动时校验失败则抛出异常终止应用。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validateAuthMode() {
        log.info("当前认证模式: {}, 激活的 Profile: {}", authMode, activeProfile);

        // 校验认证模式是否合法
        if (!isValidMode(authMode)) {
            throw new IllegalStateException("无效的认证模式: " + authMode + 
                    "，允许的值: MOCK, OIDC, LDAP_AD, CAS, CUSTOMER_IAM, LOCAL_IAM_EXTENSION");
        }

        // staging/prod 环境禁止 MOCK
        if (isProductionProfile() && MODE_MOCK.equalsIgnoreCase(authMode)) {
            throw new IllegalStateException(
                    "生产环境禁止使用 MOCK 认证模式！当前 Profile: " + activeProfile + 
                    "，请配置 yutong.auth.mode 为 OIDC/LDAP_AD/CAS/CUSTOMER_IAM/LOCAL_IAM_EXTENSION 之一");
        }

        // 输出警告信息
        if (MODE_MOCK.equalsIgnoreCase(authMode)) {
            log.warn("当前使用 MOCK 认证模式，仅适用于 local/test 环境，禁止用于生产部署");
        } else {
            log.info("生产认证模式已启用: {}", authMode);
        }
    }

    /**
     * 判断是否为生产环境 Profile
     * 
     * @return 是否为生产环境
     */
    private boolean isProductionProfile() {
        if (activeProfile == null) {
            return false;
        }
        String profile = activeProfile.toLowerCase();
        return profile.contains("staging") || profile.contains("prod") || 
               profile.contains("production") || profile.contains("ga");
    }

    /**
     * 判断认证模式是否合法
     * 
     * @param mode 认证模式
     * @return 是否合法
     */
    private boolean isValidMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return false;
        }
        String upperMode = mode.toUpperCase();
        return MODE_MOCK.equals(upperMode) || 
               MODE_OIDC.equals(upperMode) || 
               MODE_LDAP_AD.equals(upperMode) || 
               MODE_CAS.equals(upperMode) || 
               MODE_CUSTOMER_IAM.equals(upperMode) || 
               MODE_LOCAL_IAM_EXTENSION.equals(upperMode);
    }

    /**
     * 获取当前认证模式
     * 
     * @return 认证模式
     */
    public String getAuthMode() {
        return authMode;
    }

    /**
     * 判断当前是否为 Mock 模式
     * 
     * @return 是否为 Mock 模式
     */
    public boolean isMockMode() {
        return MODE_MOCK.equalsIgnoreCase(authMode);
    }

    /**
     * 判断当前是否为 OIDC 模式
     * 
     * @return 是否为 OIDC 模式
     */
    public boolean isOidcMode() {
        return MODE_OIDC.equalsIgnoreCase(authMode);
    }

    /**
     * 判断当前是否为 LDAP 模式
     * 
     * @return 是否为 LDAP 模式
     */
    public boolean isLdapMode() {
        return MODE_LDAP_AD.equalsIgnoreCase(authMode);
    }
}
