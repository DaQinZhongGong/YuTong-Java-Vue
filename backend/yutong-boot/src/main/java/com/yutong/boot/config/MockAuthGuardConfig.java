package com.yutong.boot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

/**
 * Mock 鉴权安全校验。
 * 设计来源: product-baseline.yaml forbiddenModes、98-后端实现蓝图代码评审阻断清单
 * 规则: Mock 鉴权仅允许 local/test profile；staging/prod 启用 Mock 时启动失败。
 */
@Configuration
public class MockAuthGuardConfig {

    @Value("${yutong.auth.mock.enabled-profiles:local,test}")
    private String mockEnabledProfiles;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @PostConstruct
    public void validate() {
        // 简化校验: yutong.auth.mode=MOCK 时仅允许 local/test
        // 实际生产 AuthAdapter 实现后由 Bean 条件装配保证
        String mode = System.getProperty("yutong.auth.mode", System.getenv().getOrDefault("YUTONG_AUTH_MODE", "MOCK"));
        if ("MOCK".equalsIgnoreCase(mode)) {
            String[] allowed = mockEnabledProfiles.split(",");
            boolean ok = false;
            for (String p : allowed) {
                if (p.trim().equalsIgnoreCase(activeProfile)) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                throw new IllegalStateException(
                        "Mock 鉴权禁止在 profile=" + activeProfile + " 启用；生产环境必须使用真实 AuthAdapter。详见 product-baseline.yaml forbiddenModes。");
            }
        }
    }
}
