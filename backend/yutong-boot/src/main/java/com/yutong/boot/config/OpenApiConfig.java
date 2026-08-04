package com.yutong.boot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 配置。设计来源: 27-OpenAPI与前端类型生成规范、55-OpenAPI接口Schema与Mock详设
 * 路径统一 /api/v1，ID 类型统一 string。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI yutongOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("YuTong Platform API")
                        .description("YuTong 雨桐 - 商业级全栈技术底座 API 契约")
                        .version("0.2.0")
                        .contact(new Contact().name("YuTong Team")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("MOCK")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
