package com.yutong.ai.trace.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 链路节点明细。设计来源: V043 ai_trace_node
 */
@Getter
@Setter
@TableName("ai_trace_node")
public class AiTraceNode extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 所属追踪主表 ID (ai_trace_run.id) */
    private String runId;

    /** 节点类型: model/rag/mcp/skill/tool/http/sql/human/condition/parallel/llm/media/custom */
    private String nodeType;

    /** 节点输入 JSON */
    @TableField("input_json")
    private String inputJson;

    /** 节点输出 JSON */
    @TableField("output_json")
    private String outputJson;

    /** 节点状态: PENDING/RUNNING/SUCCESS/FAILED/SKIPPED/CANCELLED */
    private String status;

    /** 节点耗时毫秒 */
    private Integer latencyMs;

    /** 失败信息 */
    private String errorMessage;
}
