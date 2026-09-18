package com.yutong.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 智能体实体。设计来源: V039 ai_agent
 * 约束: 租户内 agent_code 唯一; agent_type in react/supervisor/sequence/parallel/condition; status DRAFT/PUBLISHED。
 */
@Getter
@Setter
@TableName(value = "ai_agent", autoResultMap = true)
public class AiAgent extends BaseEntity {

    public static final String TYPE_REACT = "react";
    public static final String TYPE_SUPERVISOR = "supervisor";
    public static final String TYPE_SEQUENCE = "sequence";
    public static final String TYPE_PARALLEL = "parallel";
    public static final String TYPE_CONDITION = "condition";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 智能体编码，租户内唯一 */
    private String agentCode;

    /** 智能体名称 */
    private String agentName;

    /** 智能体类型: react/supervisor/sequence/parallel/condition */
    private String agentType;

    /** 系统提示词 */
    private String systemPrompt;

    /** 绑定工具编码数组 (jsonb) */
    @TableField("tool_ids")
    private String toolIds;

    /** 绑定 Skill 编码数组 (jsonb) */
    @TableField("skill_ids")
    private String skillIds;

    /** 绑定 MCP 服务编码数组 (jsonb) */
    @TableField("mcp_server_ids")
    private String mcpServerIds;

    /** 记忆配置 JSON: {windowSize, summarizeThreshold} */
    @TableField("memory_config_json")
    private String memoryConfigJson;

    /** 思考等级: NONE 不思考 / LOW 低 / MEDIUM 中 / HIGH 高 (影响 LLM reasoning effort) */
    @TableField("thinking_level")
    private String thinkingLevel;

    /** 状态: DRAFT/PUBLISHED */
    private String status;

    /** 思考等级常量 */
    public static final String THINKING_NONE = "NONE";
    public static final String THINKING_LOW = "LOW";
    public static final String THINKING_MEDIUM = "MEDIUM";
    public static final String THINKING_HIGH = "HIGH";

    /** 获取思考等级 (带默认值 MEDIUM) */
    public String getThinkingLevelOrDefault() {
        return (thinkingLevel == null || thinkingLevel.isBlank()) ? THINKING_MEDIUM : thinkingLevel;
    }
}
