package com.yutong.ai.aiflow.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * AIFlow 实例实体。设计来源: V040 aiflow_instance
 * status: RUNNING/SUCCESS/FAILED/CANCELLED;
 * dag_snapshot 为执行时定义快照，node_states 为 per-node 状态。
 */
@Getter
@Setter
@TableName(value = "aiflow_instance", autoResultMap = true)
public class AiflowInstance extends BaseEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 所属流程定义 ID */
    @TableField("flow_id")
    private String flowId;

    /** 实例状态 */
    private String status;

    /** 输入 JSON */
    @TableField("input_json")
    private String inputJson;

    /** 输出 JSON */
    @TableField("output_json")
    private String outputJson;

    /** DAG 快照 */
    @TableField("dag_snapshot")
    private String dagSnapshot;

    /** 节点状态 JSON: {nodeId: {status, output, error}} */
    @TableField("node_states")
    private String nodeStates;

}
