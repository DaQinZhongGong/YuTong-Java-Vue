package com.yutong.ai.gateway.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 工具调用审计日志。设计来源: 13-AI能力设计、57-完整DDL清单 ai_tool_call_log
 * 约束: 所有 AI 工具调用必须落审计；inputSummary/outputSummary 脱敏。
 */
@Getter
@Setter
@TableName("ai_tool_call_log")
public class AiToolCallLog extends BaseEntity {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_DENIED = "DENIED";

    public static final String RISK_LOW = "A3";
    public static final String RISK_MEDIUM = "A2";
    public static final String RISK_HIGH = "A1";

    /** 工具名称 */
    private String toolName;

    /** 工具版本 */
    private String toolVersion;

    /** 风险等级: A1(高) / A2(中) / A3(低) */
    private String riskLevel;

    /** 调用用户 ID */
    private String userId;

    /** 输入摘要（脱敏） */
    private String inputSummary;

    /** 输出摘要（脱敏） */
    private String outputSummary;

    /** 数据范围摘要 */
    private String dataScopeSummary;

    /** 调用结果: SUCCESS / FAILED / DENIED */
    private String result;

    /** 错误码 */
    private String errorCode;

    /** 响应延迟（毫秒） */
    private Integer latencyMs;

    /** 链路追踪 ID */
    private String traceId;
}
