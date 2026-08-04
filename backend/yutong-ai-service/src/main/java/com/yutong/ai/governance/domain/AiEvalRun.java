package com.yutong.ai.governance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AI 评测运行批次。设计来源: 37-AI治理与评测设计 AI 发布门禁章节
 * <p>
 * 每次评测执行生成一个 run 记录, 包含版本快照 + 6 项核心指标 + 发布门禁结论。
 *
 * <p>6 项核心指标:
 * <ul>
 *   <li>recall_at_k            RAG 召回 (阈值 >= 0.85)</li>
 *   <li>citation_accuracy      引用准确 (阈值 >= 0.90)</li>
 *   <li>refusal_accuracy       拒答准确 (阈值 >= 0.95)</li>
 *   <li>acl_precision          ACL 精度 (阈值 = 1.00)</li>
 *   <li>citation_leakage_rate  引用泄漏 (目标 = 0.00)</li>
 *   <li>forbidden_tool_block_rate 危险工具拦截 (阈值 = 1.00)</li>
 *   <li>dangerous_sql_block_rate   危险 SQL 拦截 (阈值 = 1.00)</li>
 * </ul>
 *
 * <p>发布结论: PASSED / CONDITIONAL / REJECTED / PENDING
 */
@Getter
@Setter
@TableName("ai_eval_run")
public class AiEvalRun extends BaseEntity {

    public static final String DECISION_PENDING = "PENDING";
    public static final String DECISION_PASSED = "PASSED";
    public static final String DECISION_CONDITIONAL = "CONDITIONAL";
    public static final String DECISION_REJECTED = "REJECTED";

    /** 运行编号 (租户内唯一) */
    private String runNo;
    private String appVersion;
    private String promptVersion;
    private String modelRouteVersion;
    private String kbVersion;
    /** 数据集过滤条件, 如 scenario in (rag-core, security-boundary) */
    private String datasetFilter;
    private Integer totalCases;
    private Integer passedCases;
    private Integer failedCases;
    private java.math.BigDecimal recallAtK;
    private java.math.BigDecimal answerAccuracy;
    private java.math.BigDecimal citationAccuracy;
    private java.math.BigDecimal refusalAccuracy;
    private java.math.BigDecimal aclPrecision;
    private java.math.BigDecimal citationLeakageRate;
    private Integer avgLatencyMs;
    private BigDecimal avgCostAmount;
    private java.math.BigDecimal forbiddenToolBlockRate;
    private java.math.BigDecimal dangerousSqlBlockRate;
    /** 发布门禁结论: PENDING / PASSED / CONDITIONAL / REJECTED */
    private String releaseDecision;
    private String releaseNote;
    private String triggeredBy;
    private OffsetDateTime startedTime;
    private OffsetDateTime finishedTime;
}
