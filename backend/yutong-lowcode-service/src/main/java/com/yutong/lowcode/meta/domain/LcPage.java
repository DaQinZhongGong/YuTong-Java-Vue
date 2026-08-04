package com.yutong.lowcode.meta.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 低代码页面配置。设计来源: 14-低代码平台设计、57-完整DDL清单 lc_page
 * 状态: DRAFT → PUBLISHED；回滚 = 历史版本复制为草稿后重新发布
 */
@Getter
@Setter
@TableName("lc_page")
public class LcPage extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    public static final String PAGE_TYPE_LIST = "LIST";
    public static final String PAGE_TYPE_FORM = "FORM";
    public static final String PAGE_TYPE_DETAIL = "DETAIL";

    private String pageCode;

    private String pageName;

    private String entityId;

    /** LIST / FORM / DETAIL */
    private String pageType;

    /** 布局配置 JSON */
    private String layoutJson;

    private String layoutSchemaVersion;

    private Integer versionNo;

    private String status;

    private OffsetDateTime publishedTime;
}
