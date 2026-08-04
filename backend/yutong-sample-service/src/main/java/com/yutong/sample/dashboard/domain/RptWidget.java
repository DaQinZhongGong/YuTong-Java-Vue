package com.yutong.sample.dashboard.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Widget 组件定义。设计来源: 42-报表与大屏可视化设计 rpt_widget。
 * <p>
 * Widget 是可复用的可视化组件，用于报表设计器和大屏设计器。
 * 支持类型: line-chart/bar-chart/pie-chart/kpi-card/data-table/text。
 */
@Getter
@Setter
@TableName("rpt_widget")
public class RptWidget extends BaseEntity {

    public static final String TYPE_LINE_CHART = "line-chart";
    public static final String TYPE_BAR_CHART = "bar-chart";
    public static final String TYPE_PIE_CHART = "pie-chart";
    public static final String TYPE_KPI_CARD = "kpi-card";
    public static final String TYPE_DATA_TABLE = "data-table";
    public static final String TYPE_TEXT = "text";

    /** 唯一编码 */
    private String widgetCode;

    private String widgetName;

    /** Widget 类型: line-chart/bar-chart/pie-chart/kpi-card/data-table/text */
    private String widgetType;

    /** 关联的数据集编码 */
    private String datasetCode;

    /** 属性 JSON (jsonb 序列化为字符串) */
    private String propsJson;

    /** 样式 JSON (jsonb 序列化为字符串) */
    private String styleJson;

    private String description;
}
