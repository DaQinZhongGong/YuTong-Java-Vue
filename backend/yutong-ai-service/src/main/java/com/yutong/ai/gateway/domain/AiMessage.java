package com.yutong.ai.gateway.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import com.yutong.infra.persistence.JsonbTypeHandler;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * AI 会话消息。设计来源: 13-AI能力设计、57-完整DDL清单 ai_message
 * 约束: contentEncrypted 存储加密内容，contentSummary 供列表展示。
 */
@Getter
@Setter
@TableName(value = "ai_message", autoResultMap = true)
public class AiMessage extends BaseEntity {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /** 所属会话 ID */
    private String conversationId;

    /** 角色: system / user / assistant */
    private String role;

    /** 内容摘要（脱敏） */
    private String contentSummary;

    /** 加密后的完整内容 */
    private String contentEncrypted;

    /** 引用来源 JSON（jsonb 列，使用 JsonbTypeHandler 避免 varchar/jsonb 类型不匹配） */
    @TableField(typeHandler = JsonbTypeHandler.class)
    private String citationJson;

    /** 输入 token 数 */
    private Integer tokenInput;

    /** 输出 token 数 */
    private Integer tokenOutput;

    /** 成本金额 */
    private BigDecimal costAmount;

    /** 响应延迟（毫秒） */
    private Integer latencyMs;
}
