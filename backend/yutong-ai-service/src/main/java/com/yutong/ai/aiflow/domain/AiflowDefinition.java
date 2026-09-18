package com.yutong.ai.aiflow.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * AIFlow 定义实体。设计来源: V040 aiflow_definition
 * 约束: 租户内 flow_code + version_no 唯一; status DRAFT/PUBLISHED/ARCHIVED;
 * dag_json 含 nodes/edges，节点类型 >= model/rag/mcp/skill/email/human/sql/http + condition/parallel
 *
 * <p>融合路径说明: 本模块与 yutong-workflow-service 的 LightWorkflowEngine 解耦并存；
 * LightWorkflowEngine 负责 BPMN 人工审批流 (UserTask/ExclusiveGateway)，AIFlow 负责 AI DAG 编排
 * (model/rag/mcp 等)。未来融合可通过 LightWorkflowEngine 暴露 SPI 或在 AIFlow 中以
 * human 节点委托 workflow，完成 human-in-the-loop 闭环，当前保持 untouched。</p>
 */
@Getter
@Setter
@TableName(value = "aiflow_definition", autoResultMap = true)
public class AiflowDefinition extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 流程编码，租户内 code+version 唯一 */
    private String flowCode;

    /** 流程名称 */
    private String flowName;

    /** 业务版本号 */
    private Integer versionNo;

    /** DAG JSON: {nodes:[{id,type,config}], edges:[{source,target,condition}]} */
    @TableField("dag_json")
    private String dagJson;

    /** 状态: DRAFT/PUBLISHED/ARCHIVED */
    private String status;

    // Node type constants (用于校验与文档)
    public static final String NODE_MODEL = "model";
    public static final String NODE_RAG = "rag";
    public static final String NODE_MCP = "mcp";
    public static final String NODE_SKILL = "skill";
    public static final String NODE_EMAIL = "email";
    public static final String NODE_HUMAN = "human";
    public static final String NODE_SQL = "sql";
    public static final String NODE_HTTP = "http";
    public static final String NODE_CONDITION = "condition";
    public static final String NODE_PARALLEL = "parallel";

}
