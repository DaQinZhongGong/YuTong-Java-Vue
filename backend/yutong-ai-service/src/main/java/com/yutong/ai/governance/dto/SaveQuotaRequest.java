package com.yutong.ai.governance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 保存 AI 成本额度配置请求。设计来源: 37-AI治理与评测设计 成本治理章节
 */
@Data
public class SaveQuotaRequest {

    private String id;

    /** TENANT / USER / SCENARIO */
    @NotBlank(message = "额度维度不能为空")
    private String quotaScope;

    @NotBlank(message = "维度键不能为空 (tenantId / userId / scenarioCode)")
    private String scopeKey;

    /** 模型编码, 可空表示适用所有模型 */
    private String modelCode;

    private Long dailyTokenLimit = 1_000_000L;
    private BigDecimal dailyCostLimit = new BigDecimal("100.0000");
    private Integer singleCallTokenLimit = 8000;
    private String currency = "CNY";
    private Boolean enabled = true;
    private Integer version;
}
