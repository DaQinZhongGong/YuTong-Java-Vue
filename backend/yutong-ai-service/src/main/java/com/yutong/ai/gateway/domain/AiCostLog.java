package com.yutong.ai.gateway.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * AI 成本日志。设计来源: 13-AI能力设计、57-完整DDL清单 ai_cost_log
 * 约束: 每次 AI 调用必须记录成本，用于成本分析与配额控制。
 */
@Getter
@Setter
@TableName("ai_cost_log")
public class AiCostLog extends BaseEntity {

    /** 供应商编码 */
    private String providerCode;

    /** 模型编码 */
    private String modelCode;

    /** 使用场景 */
    private String scenario;

    /** 调用用户 ID */
    private String userId;

    /** 关联会话 ID */
    private String conversationId;

    /** 输入 token 数 */
    private Integer tokenInput;

    /** 输出 token 数 */
    private Integer tokenOutput;

    /** 成本金额 */
    private BigDecimal costAmount;

    /** 币种 */
    private String currency;

    /** 响应延迟（毫秒） */
    private Integer latencyMs;

    /** 调用结果 */
    private String result;
}
