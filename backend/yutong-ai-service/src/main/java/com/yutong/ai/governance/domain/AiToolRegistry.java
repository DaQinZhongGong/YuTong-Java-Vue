package com.yutong.ai.governance.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 工具注册表。设计来源: 37-AI治理与评测设计 AI 工具治理章节
 * <p>
 * 替代 GA2-13 硬编码的 AiToolRegistry 内存 Map, 支持运行时启停和审计。
 * 6 个禁止工具由 is_forbidden=true 标记, 6 个白名单工具由 enabled=true 标记。
 *
 * <p>风险等级: LOW / MEDIUM / HIGH / CRITICAL
 * <p>AI 能力等级: A0~A4 (37 号文档分级, A4 禁止第一版)
 */
@Getter
@Setter
@TableName("ai_tool_registry")
public class AiToolRegistry extends BaseEntity {

    public static final String RISK_LOW = "LOW";
    public static final String RISK_MEDIUM = "MEDIUM";
    public static final String RISK_HIGH = "HIGH";
    public static final String RISK_CRITICAL = "CRITICAL";

    public static final String AI_LEVEL_A0 = "A0";
    public static final String AI_LEVEL_A1 = "A1";
    public static final String AI_LEVEL_A2 = "A2";
    public static final String AI_LEVEL_A3 = "A3";
    public static final String AI_LEVEL_A4 = "A4";

    /** 工具名（租户内唯一） */
    private String toolName;
    private String toolVersion;
    /** LOW / MEDIUM / HIGH / CRITICAL */
    private String riskLevel;
    /** A0~A4 */
    private String aiCapabilityLevel;
    private String permissionCode;
    private String description;
    /** 输入 JSON Schema */
    private String inputSchema;
    /** 输出 JSON Schema */
    private String outputSchema;
    private Boolean isReadonly;
    private Boolean needsHumanReview;
    private Boolean accessBusinessData;
    private String dataScopeStrategy;
    private String fieldMaskingStrategy;
    private Integer maxResults;
    private Integer timeoutMs;
    private Integer rateLimitPerMin;
    /** 是否禁止工具（37 号文档明令禁止的 6 类） */
    private Boolean isForbidden;
    private String forbiddenReason;
    private Boolean enabled;
    private String ownerUserId;
}
