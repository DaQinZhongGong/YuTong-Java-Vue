package com.yutong.lowcode.plugin.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 模板市场实体（45 号文档「插件与模板生态设计」）。
 * 保存模板元数据、分类、预览图、安装包地址。
 * 状态: DRAFT → PUBLISHED
 */
@Getter
@Setter
@TableName("mkt_template")
public class MktTemplate extends BaseEntity {

    // ===== category 分类 =====
    public static final String CATEGORY_BUSINESS = "BUSINESS";
    public static final String CATEGORY_PAGE = "PAGE";
    public static final String CATEGORY_INDUSTRY = "INDUSTRY";
    public static final String CATEGORY_THEME = "THEME";

    // ===== status 生命周期 =====
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    /** 模板编码，租户内唯一 */
    private String templateCode;

    private String templateName;

    /** BUSINESS / PAGE / INDUSTRY / THEME */
    private String category;

    /** 预览图 URL 列表（JSON 数组） */
    private String previewImages;

    /** 模板安装包下载地址 */
    private String packageUrl;

    /** 最低平台版本要求 */
    private String minVersion;

    /** 安装次数统计 */
    private Integer installCount;

    /** DRAFT / PUBLISHED */
    private String status;
}
