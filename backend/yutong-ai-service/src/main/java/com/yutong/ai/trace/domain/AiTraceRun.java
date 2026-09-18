package com.yutong.ai.trace.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 链路追踪主表。设计来源: V043 ai_trace_run
 */
@Getter
@Setter
@TableName("ai_trace_run")
public class AiTraceRun extends BaseEntity {

    public static final String TYPE_AGENT = "AGENT";
    public static final String TYPE_FLOW = "FLOW";
    public static final String TYPE_RAG = "RAG";
    public static final String TYPE_MCP = "MCP";
    public static final String TYPE_SKILL = "SKILL";
    public static final String TYPE_TOOL = "TOOL";
    public static final String TYPE_LLM = "LLM";
    public static final String TYPE_MEDIA = "MEDIA";
    public static final String TYPE_CUSTOM = "CUSTOM";

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 链路类型: AGENT/FLOW/RAG/MCP/SKILL/TOOL/LLM/MEDIA/CUSTOM */
    private String traceType;

    /** 输入 JSON：含 prompt/params/context 等 */
    @TableField("input_json")
    private String inputJson;

    /** 输出 JSON：含 result/error/tokens 等 */
    @TableField("output_json")
    private String outputJson;

    /** 状态: RUNNING/SUCCESS/FAILED/CANCELLED */
    private String status;

    /** 总耗时毫秒 */
    private Integer latencyMs;

    /** 失败信息 */
    private String errorMessage;
}
