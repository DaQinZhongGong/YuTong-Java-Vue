package com.yutong.sample.dashboard.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 大屏定义。设计来源: 42-报表与大屏可视化设计 rpt_dashboard。
 * <p>
 * R3 数据大屏能力: 全屏可视化、拖拽组件、自动轮播、刷新间隔配置。
 * 画布固定 1920×1080，支持深色科技风/浅色政务风两套主题。
 */
@Getter
@Setter
@TableName("rpt_dashboard")
public class RptDashboard extends BaseEntity {

    public static final String THEME_DARK = "dark";
    public static final String THEME_LIGHT = "light";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 唯一编码 */
    private String dashboardCode;

    private String dashboardName;

    /** 画布宽度，默认 1920 */
    private Integer canvasWidth;

    /** 画布高度，默认 1080 */
    private Integer canvasHeight;

    /** 主题: dark/light */
    private String theme;

    /** 背景图片 URL */
    private String backgroundImage;

    /** 布局 JSON (jsonb 序列化为字符串) */
    private String layoutJson;

    /** 组件绑定 JSON (jsonb 序列化为字符串) */
    private String componentBindings;

    /** 刷新间隔（秒），默认 30 */
    private Integer refreshInterval;

    /** DRAFT/PUBLISHED */
    private String status;

    /** 负责人用户 ID */
    private String ownerUserId;

    /** 查看大屏所需权限码 */
    private String permissionCode;

    private String description;
}
