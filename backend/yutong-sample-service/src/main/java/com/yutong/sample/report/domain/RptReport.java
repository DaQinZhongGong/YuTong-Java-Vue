package com.yutong.sample.report.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 报表定义。设计来源: 35-样例业务矩阵扩展设计 P1 报表分析、42-报表与大屏可视化设计 rpt_report。
 * <p>
 * GA2-36 验证能力: 报表 CRUD + 发布 + 渲染 + 异步导出 + AI 指标解释。
 * <p>
 * 不做 R2 拖拽设计器、R3 大屏、R4 订阅（按 42 号文档第一版范围）。
 */
@Getter
@Setter
@TableName("rpt_report")
public class RptReport extends BaseEntity {

    public static final String TYPE_TABLE = "TABLE";
    public static final String TYPE_CHART = "CHART";
    public static final String TYPE_MIX = "MIX";
    public static final String TYPE_DASHBOARD = "DASHBOARD";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 唯一编码 */
    private String reportCode;

    private String reportName;

    /** TABLE / CHART / MIX / DASHBOARD */
    private String reportType;

    /** 布局 JSON (jsonb 序列化为字符串): {canvas, components:[{type, datasetCode, props, layout}]} */
    private String layoutJson;

    private Integer versionNo;

    /** 查看报表所需权限码 */
    private String permissionCode;

    /** DRAFT/PUBLISHED */
    private String status;

    private String description;
}
