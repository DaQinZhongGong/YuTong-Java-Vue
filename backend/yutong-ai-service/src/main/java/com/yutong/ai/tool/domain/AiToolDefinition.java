package com.yutong.ai.tool.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 本地工具注册实体。设计来源: V038 ai_tool_definition
 * 约束: 租户内 tool_code 唯一; handler_class 指向 Spring Bean。
 */
@Getter
@Setter
@TableName("ai_tool_definition")
public class AiToolDefinition extends BaseEntity {

    /** 工具编码，租户内唯一 */
    private String toolCode;

    /** 工具名称 */
    private String toolName;

    /** 工具描述，供 LLM function calling */
    private String description;

    /** 输入 JSON Schema (jsonb) */
    @TableField("input_schema")
    private String inputSchema;

    /** 处理器类全限定名 */
    private String handlerClass;

    /** 是否启用 */
    private Boolean enabled;
}
