package com.yutong.system.license.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 商业授权模块配置。注册 {@link LicenseProperties} 为 Spring Bean。
 *
 * <p>设计来源: 70-商业授权与版本能力裁剪详设、98-后端实现蓝图与代码骨架详设。
 * GA2-L170 落地。
 */
@Configuration
@EnableConfigurationProperties(LicenseProperties.class)
public class LicenseConfig {
}
