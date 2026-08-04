package com.yutong.ai.governance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 评测样本。设计来源: 37-AI治理与评测设计 评测集结构与执行产物章节
 * <p>
 * GA 全量 230 条基线:
 * <ul>
 *   <li>rag-core 140 条 (架构/API/数据/低代码/部署运维/安全边界/无依据 各 20)</li>
 *   <li>generation-lowcode 60 条 (字段/页面/SQL 草稿 各 20)</li>
 *   <li>security-boundary 30 条</li>
 * </ul>
 *
 * <p>scenario 取值: rag-core / generation-lowcode / sql-draft / security-boundary
 * <p>样本必须含 expectedRefusal / expectedErrorCode / forbiddenTools 等安全边界字段。
 */
@Getter
@Setter
@TableName("ai_eval_dataset")
public class AiEvalDataset extends BaseEntity {

    public static final String SCENARIO_RAG_CORE = "rag-core";
    public static final String SCENARIO_GENERATION_LOWCODE = "generation-lowcode";
    public static final String SCENARIO_SQL_DRAFT = "sql-draft";
    public static final String SCENARIO_SECURITY_BOUNDARY = "security-boundary";

    public static final String DIFFICULTY_EASY = "EASY";
    public static final String DIFFICULTY_MEDIUM = "MEDIUM";
    public static final String DIFFICULTY_HARD = "HARD";

    /** 样本编号 (租户内唯一) */
    private String caseId;
    /** 场景: rag-core / generation-lowcode / sql-draft / security-boundary */
    private String scenario;
    private String locale;
    private String question;
    /** 期望答案要点 JSON 数组 */
    private String expectedAnswerPointsJson;
    /** 期望来源 JSON 数组 */
    private String expectedSourcesJson;
    /** 禁止来源 JSON 数组 (安全边界样本) */
    private String forbiddenSourcesJson;
    /** 禁止工具 JSON 数组 */
    private String forbiddenToolsJson;
    /** 权限上下文 JSON */
    private String permissionContextJson;
    /** 期望输出 Schema JSON (生成类样本) */
    private String expectedSchemaJson;
    /** 禁止字段 JSON 数组 (生成类样本) */
    private String forbiddenFieldsJson;
    /** 风险标签 JSON 数组 */
    private String riskTagsJson;
    /** 期望拒答 (无依据/越权/危险请求) */
    private Boolean expectedRefusal;
    private String expectedRefusalReason;
    private String expectedErrorCode;
    /** 断言 JSON: mustCite/forbidHallucination/maxLatencyMs/mustRefuse/mustBeReadonly 等 */
    private String assertionsJson;
    /** EASY / MEDIUM / HARD */
    private String difficulty;
    private Boolean enabled;
}
