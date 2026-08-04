package com.yutong.ai.governance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AI 成本额度配置。设计来源: 37-AI治理与评测设计 成本治理章节
 * <p>
 * 三维日额度:
 * <ul>
 *   <li>TENANT  租户维度 (scope_key = tenant_id)</li>
 *   <li>USER    用户维度 (scope_key = user_id)</li>
 *   <li>SCENARIO 场景维度 (scope_key = scenario_code, 如 chat/generate/sql-draft)</li>
 * </ul>
 * 同时支持单次调用 token 上限和金额上限, 配合 ai_cost_log 实时累计。
 */
@Getter
@Setter
@TableName("ai_cost_quota")
public class AiCostQuota extends BaseEntity {

    public static final String SCOPE_TENANT = "TENANT";
    public static final String SCOPE_USER = "USER";
    public static final String SCOPE_SCENARIO = "SCENARIO";

    /** TENANT / USER / SCENARIO */
    private String quotaScope;
    /** 依据 scope 解析: tenantId / userId / scenarioCode */
    private String scopeKey;
    /** 模型编码, 可空表示适用所有模型 */
    private String modelCode;
    /** 日 token 上限 */
    private Long dailyTokenLimit;
    /** 日金额上限 */
    private BigDecimal dailyCostLimit;
    /** 单次调用 token 上限 */
    private Integer singleCallTokenLimit;
    private String currency;
    private OffsetDateTime effectiveFrom;
    private OffsetDateTime effectiveTo;
    private Boolean enabled;
}
