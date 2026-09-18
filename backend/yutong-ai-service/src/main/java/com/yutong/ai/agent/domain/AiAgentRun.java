package com.yutong.ai.agent.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * AI 智能体执行记录。设计来源: V039 ai_agent_run
 * status: RUNNING / SUCCESS / FAILED; trace_json 为 Thought/Action/Observation 步骤数组。
 */
@Getter
@Setter
@TableName(value = "ai_agent_run", autoResultMap = true)
public class AiAgentRun extends BaseEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    /** 所属智能体 ID */
    private String agentId;

    /** 关联会话 ID (可选) */
    private String conversationId;

    /** 执行状态: RUNNING/SUCCESS/FAILED */
    private String status;

    /** 输入 JSON: {query, params} */
    @TableField("input_json")
    private String inputJson;

    /** 输出 JSON: {answer, summary} */
    @TableField("output_json")
    private String outputJson;

    /** 执行轨迹 JSON 数组: [{step, type:Thought/Action/Observation, content}] */
    @TableField("trace_json")
    private String traceJson;
}
