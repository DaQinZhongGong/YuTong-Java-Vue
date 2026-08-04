package com.yutong.sample.report.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * 报表渲染结果。设计来源: 42-报表与大屏可视化设计 /reports/{code}/render。
 * <p>
 * 前端接收 layoutJson + 各组件的 dataset 执行结果，由 ECharts 渲染图表。
 */
@Getter
@Builder
public class ReportRenderVO {

    /** 报表编码 */
    private final String reportCode;

    /** 报表名称 */
    private final String reportName;

    /** 报表类型 */
    private final String reportType;

    /** 布局 JSON（透传 rpt_report.layout_json，前端按 components 渲染） */
    private final String layoutJson;

    /** 各组件的数据：componentId -> DatasetResultVO */
    private final Map<String, DatasetResultVO> componentData;

    /** 本次渲染涉及的组件 ID 列表 */
    private final List<String> componentIds;

    /** 链路 ID */
    private final String traceId;
}
