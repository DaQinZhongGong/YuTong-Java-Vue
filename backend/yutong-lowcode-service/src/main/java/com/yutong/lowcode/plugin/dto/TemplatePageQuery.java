package com.yutong.lowcode.plugin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 模板市场分页查询条件。
 * 支持 keyword/category/status 过滤
 */
@Getter
@Setter
public class TemplatePageQuery {

    private int pageNo = 1;

    private int pageSize = 20;

    /** 模糊匹配 template_code 或 template_name */
    private String keyword;

    /** BUSINESS / PAGE / INDUSTRY / THEME */
    private String category;

    /** DRAFT / PUBLISHED */
    private String status;
}
