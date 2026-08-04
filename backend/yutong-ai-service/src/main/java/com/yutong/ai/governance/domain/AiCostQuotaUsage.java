package com.yutong.ai.governance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * AI 成本额度日用量。设计来源: GA2-45 成本治理闭环
 * <p>
 * 按租户/用户/场景 + 模型 + 日期累计 token 与金额用量，用于实时额度校验与监控统计。
 */
@Getter
@Setter
@TableName("ai_cost_quota_usage")
public class AiCostQuotaUsage extends BaseEntity {

    /** TENANT / USER / SCENARIO */
    private String quotaScope;

    /** 依据 scope 解析: tenantId / userId / scenarioCode */
    private String scopeKey;

    /** 模型编码 */
    private String modelCode;

    /** 用量日期 */
    private LocalDate usageDate;

    /** 已用 token 数 */
    private Long tokenUsed;

    /** 已用金额 */
    private BigDecimal costUsed;
}
