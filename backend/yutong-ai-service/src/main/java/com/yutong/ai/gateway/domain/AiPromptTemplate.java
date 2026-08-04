package com.yutong.ai.gateway.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Prompt 模板。设计来源: 13-AI能力设计、57-完整DDL清单 ai_prompt_template、37-AI治理与评测设计
 * 状态: DRAFT → PUBLISHED → DISABLED (GA2-45 新增 DISABLED)；发布时 versionNo 自增。
 */
@Getter
@Setter
@TableName("ai_prompt_template")
public class AiPromptTemplate extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    /** GA2-45: 37 号文档 Prompt 治理三态: DRAFT / PUBLISHED / DISABLED */
    public static final String STATUS_DISABLED = "DISABLED";

    /** 模板编码，租户内唯一 */
    private String templateCode;

    /** 模板版本号，每次发布自增 */
    private Integer versionNo;

    /** 使用场景 */
    private String scenario;

    /** 输入 JSON Schema */
    private String inputSchema;

    /** 输出 JSON Schema */
    private String outputSchema;

    /** Prompt 正文 */
    private String promptText;

    /** 安全规则约束 */
    private String safetyRules;

    /** 评测集编码 */
    private String evaluationSetCode;

    /** DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** GA2-45: 最近一次发布时间 */
    private OffsetDateTime publishedTime;

    /** GA2-45: 最近一次发布人 */
    private String publishedBy;
}
