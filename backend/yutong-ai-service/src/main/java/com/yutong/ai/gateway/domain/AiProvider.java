package com.yutong.ai.gateway.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * AI 供应商配置。设计来源: 13-AI能力设计、57-完整DDL清单 ai_provider
 * 约束: providerCode 租户内唯一; apiKeyRef 引用密钥保管箱，不落明文。
 * V036 扩展: provider_type / model_type / platform / multimodal / health / config_json 
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

    // ===== V036 平价能力 扩展字段 =====

    /** 供应商类型 11 枚举: openai/deepseek/qianwen/zhipu/ollama/minimax/atlas/xiaomi/dify/coze/custom_api */
    @TableField("provider_type")
    private String providerType;

    /** 主模型类型 9 枚举: chat/image/vector/reranker/audio/text/video/ppt/music */
    @TableField("model_type")
    private String modelType;

    /** AI 应用平台: dify/coze/fastgpt/null (null 表示直连) */
    @TableField("platform")
    private String platform;

    /** 支持的模型类型列表 JSON 数组，如 ["chat","vector"] */
    @TableField("model_type_list_json")
    private String modelTypeListJson;

    /** 多模态能力 JSON，如 {"image":true,"video":false,"audio":true,"ppt":false} 映射 /media/* */
    @TableField("multimodal_capabilities")
    private String multimodalCapabilities;

    /** 健康状态: UNKNOWN/HEALTHY/UNHEALTHY/DEGRADED，默认 UNKNOWN */
    @TableField("health_status")
    private String healthStatus;

    /** 最近一次健康检查时间 */
    @TableField("health_checked_time")
    private OffsetDateTime healthCheckedTime;

    /** 厂商特定配置 JSON (vendor-specific)，如 dify app_id 等，不落明文密钥 */
    @TableField("config_json")
    private String configJson;
}
