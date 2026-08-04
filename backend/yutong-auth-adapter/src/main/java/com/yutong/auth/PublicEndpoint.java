package com.yutong.auth;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 公开接口注解。无需登录即可访问的接口必须显式标注。
 * 设计来源: 98-后端实现蓝图 AuthContext 与租户解析
 * 仅限 /api/v1/auth/login、/api/v1/monitor/health、/actuator/health、OpenAPI 文档、静态资源
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicEndpoint {
}
