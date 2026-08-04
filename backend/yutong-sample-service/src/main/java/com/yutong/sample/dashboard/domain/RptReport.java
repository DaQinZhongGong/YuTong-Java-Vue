package com.yutong.sample.dashboard.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 报表定义（设计器扩展）。设计来源: 42-报表与大屏可视化设计 rpt_report。
 * <p>
 * R2 拖拽式报表设计器能力: 在原有 rpt_report 基础上扩展 dataset_bindings 和 owner_user_id 字段。
 * 支持类型: TABLE/CHART/MIX/DASHBOARD。
 * <p>
 * 注意: 此类与 com.yutong.sample.report.domain.RptReport 是同一个表的不同映射，
 * 用于 dashboard 模块的设计器功能，增加了 datasetBindings 字段支持。
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

    /** 数据集绑定 JSON (jsonb 序列化为字符串): [{componentId, datasetCode}] */
    private String datasetBindings;

    private Integer versionNo;

    /** 查看报表所需权限码 */
    private String permissionCode;

    /** DRAFT/PUBLISHED */
    private String status;

    /** 负责人用户 ID */
    private String ownerUserId;

    private String description;
}
