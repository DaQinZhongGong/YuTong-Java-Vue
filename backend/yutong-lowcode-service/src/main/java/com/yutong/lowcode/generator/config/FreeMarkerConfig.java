package com.yutong.lowcode.generator.config;

import freemarker.cache.ClassTemplateLoader;
import freemarker.template.TemplateExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * FreeMarker 配置。设计来源: 65-低代码代码生成模板详设
 * 加载 classpath:/templates/ 下的 .ftl 模板文件。
 */
@Configuration
public class FreeMarkerConfig {

    @Bean
    public freemarker.template.Configuration freemarkerConfiguration() {
        freemarker.template.Configuration cfg =
                new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_34);
        cfg.setTemplateLoader(new ClassTemplateLoader(getClass(), "/templates"));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setOutputEncoding("UTF-8");
        cfg.setNumberFormat("0.######");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        cfg.setLogTemplateExceptions(false);
        cfg.setWrapUncheckedExceptions(true);
        cfg.setFallbackOnNullLoopVariable(false);
        return cfg;
    }
}
