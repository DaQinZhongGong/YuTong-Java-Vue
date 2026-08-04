package com.yutong.boot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson ObjectMapper 配置。
 *
 * <p>设计来源: 17-平台基础能力详细设计、43-国际化与无障碍设计、98-后端实现蓝图。
 *
 * <p>GA2-14 落地: 在 Spring Boot 4 部分自动配置场景下 ObjectMapper Bean 可能不可用
 * （参考 AuditableAspect 注释），导致 DictService/SysConfigService 等需要将业务对象
 * 序列化到 Redis 的服务无法注入 ObjectMapper。之前这些服务使用 {@code new ObjectMapper()}
 * 应急，但默认 ObjectMapper 不支持 Java 8 时间类型（OffsetDateTime/LocalDateTime 等），
 * 序列化 BaseEntity 时静默失败被 try-catch 吞掉，导致 Redis 缓存键始终写不进去。
 *
 * <p>本配置类显式声明一个 ObjectMapper Bean，注册 {@link JavaTimeModule}，
 * 让所有需要序列化 BaseEntity 子类的服务都能正确处理 OffsetDateTime 字段。
 *
 * <p>时区与日期格式遵循 application.yml 中 {@code spring.jackson.time-zone=Asia/Shanghai}
 * 与 {@code spring.jackson.date-format=yyyy-MM-dd HH:mm:ss} 配置。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
