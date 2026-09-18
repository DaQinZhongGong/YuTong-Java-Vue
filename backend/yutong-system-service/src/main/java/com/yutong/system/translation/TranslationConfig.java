package com.yutong.system.translation;

import com.yutong.common.translation.TranslationCache;
import com.yutong.common.translation.TranslationRegistry;
import com.yutong.common.translation.TranslationType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 字典翻译注册中心 — 把 5 个默认 TranslationService 装入 TranslationRegistry
 * 设计来源: ADR 0004 P2-A 批次 2-B
 *
 * 落点:84-字典/用户/部门显示名自动化详设
 *
 * 启动时自动装配:
 *   - DICT  -> DefaultDictTranslationService
 *   - USER  -> DefaultUserTranslationService
 *   - DEPT  -> DefaultDeptTranslationService
 *   - POST  -> DefaultPostTranslationService
 *   - ROLE  -> DefaultRoleTranslationService
 *
 * 业务方在 VO 加 @Translation 注解即可开箱即用
 */
@Configuration
public class TranslationConfig {

    @Bean
    public TranslationCache translationCache() {
        return new TranslationCache();
    }

    @Bean
    public TranslationRegistry translationRegistry(
            DefaultDictTranslationService dictSvc,
            DefaultUserTranslationService userSvc,
            DefaultDeptTranslationService deptSvc,
            DefaultPostTranslationService postSvc,
            DefaultRoleTranslationService roleSvc) {
        TranslationRegistry reg = new TranslationRegistry();
        reg.register(TranslationType.DICT, dictSvc);
        reg.register(TranslationType.USER, userSvc);
        reg.register(TranslationType.DEPT, deptSvc);
        reg.register(TranslationType.POST, postSvc);
        reg.register(TranslationType.ROLE, roleSvc);
        return reg;
    }
}
