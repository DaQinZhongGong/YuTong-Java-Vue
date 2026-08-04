package com.yutong.ai.governance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * AI 评测样本结果。设计来源: 37-AI治理与评测设计 评测集结构与执行产物章节
 * <p>
 * 每条评测样本执行后写入一条结果, 记录实际回答/引用/工具调用/拒答/失败原因等,
 * 用于失败样本整改闭环 (37 号文档: 失败样本不得只在聊天记录中修正)。
 */
@Getter
@Setter
@TableName("ai_eval_result")
public class AiEvalResult extends BaseEntity {

    /** 关联 ai_eval_run.id */
    private String runId;
    private String caseId;
    private String scenario;
    private String actualAnswer;
    /** 实际引用来源 JSON 数组 */
    private String actualSourcesJson;
    /** 实际调用工具 JSON 数组 */
    private String actualToolsJson;
    private Boolean isRefused;
    private String refusalReason;
    private Boolean isPassed;
    private String failureReason;
    private Integer latencyMs;
    private BigDecimal costAmount;
    private Integer tokenInput;
    private Integer tokenOutput;
    private String errorCode;
    private String traceId;
}
