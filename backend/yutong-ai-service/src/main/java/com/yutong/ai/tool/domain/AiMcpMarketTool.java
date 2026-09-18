package com.yutong.ai.tool.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * MCP 市场工具明细。设计来源: V043 ai_mcp_market_tool
 */
@Getter
@Setter
@TableName("ai_mcp_market_tool")
public class AiMcpMarketTool extends BaseEntity {

    /** 所属市场 ID (ai_mcp_market.id) */
    private String marketId;

    /** 工具名称，市场内唯一 */
    private String toolName;

    /** 工具描述，供 LLM function calling 展示 */
    private String toolDesc;

    /** 输入 JSON Schema (jsonb)，前端可视化配置实时生效 */
    @TableField("input_schema")
    private String inputSchema;
}
