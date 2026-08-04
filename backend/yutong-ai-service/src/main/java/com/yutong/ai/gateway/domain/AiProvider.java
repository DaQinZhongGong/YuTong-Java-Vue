package com.yutong.ai.gateway.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 供应商配置。设计来源: 13-AI能力设计、57-完整DDL清单 ai_provider
 * 约束: providerCode 租户内唯一; apiKeyRef 引用密钥保管箱，不落明文。
 */
@Getter
@Setter
@TableName("ai_provider")
public class AiProvider extends BaseEntity {

    /** 供应商编码，租户内唯一 */
    private String providerCode;

    /** 供应商名称 */
    private String providerName;

    /** 服务端点 URL */
    private String endpoint;

    /** 密钥引用标识，指向密钥保管箱 */
    private String apiKeyRef;

    /** 可用模型列表 JSON */
    private String modelListJson;

    /** 协议: OPENAI_COMPATIBLE / CUSTOM；默认 OPENAI_COMPATIBLE */
    private String protocol;

    /** 是否启用 */
    private Boolean enabled;

    /** 优先级，数值越小优先级越高 */
    private Integer priority;

    /** 超时时间（毫秒） */
    private Integer timeoutMs;

    /** 每分钟速率限制 */
    private Integer rateLimitPerMin;
}
