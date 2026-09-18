package com.yutong.system.cms.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * CMS 内容。设计来源: platform-ai CmsContent + 84-CMS 运营详设
 * <p>
 * 用于站点内容管理: 公告、帮助文档、条款、博客等。
 * 支持 Markdown 原文 + 渲染缓存 + 多分类。
 * 状态: DRAFT → PUBLISHED → ARCHIVED
 */
@Getter
@Setter
@TableName("cms_content")
public class CmsContent extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";

    /** 标题 */
    private String title;

    /** URL slug (用于 /cms/{slug}), 租户内唯一 */
    @TableField("slug")
    private String slug;

    /** Markdown 原文 */
    @TableField("content_md")
    private String contentMd;

    /** 渲染后的 HTML (缓存, 减少重复渲染) */
    @TableField("content_html")
    private String contentHtml;

    /** 摘要 (列表展示用, 200 字内) */
    private String summary;

    /** 分类: announcement/help/terms/blog/... */
    private String category;

    /** 标签, 逗号分隔 */
    private String tags;

    /** 状态: DRAFT/PUBLISHED/ARCHIVED */
    private String status;

    /** 作者 userId */
    @TableField("author_id")
    private String authorId;

    /** 浏览次数 */
    @TableField("view_count")
    private Long viewCount;

    /** 发布时间 */
    @TableField("published_at")
    private LocalDateTime publishedAt;
}
