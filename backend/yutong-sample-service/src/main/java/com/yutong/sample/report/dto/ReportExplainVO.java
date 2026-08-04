package com.yutong.sample.report.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 报表 AI 指标解释结果。设计来源: 35-样例业务矩阵扩展设计 P1 报表分析（AI 指标解释能力）。
 * <p>
 * AI 解读报表指标含义、异常波动。SQL 数据集只允许 AI 生成草稿，人工确认后保存。
 */
@Getter
@Builder
public class ReportExplainVO {

    /** 报表编码 */
    private final String reportCode;

    /** AI 生成的解释文本 */
    private final String explanation;

    /** 数据生成时间 */
    private final OffsetDateTime generatedTime;

    /** 链路 ID */
    private final String traceId;

    /** 是否降级（AI 不可用时返回降级提示） */
    private final boolean degraded;
}
