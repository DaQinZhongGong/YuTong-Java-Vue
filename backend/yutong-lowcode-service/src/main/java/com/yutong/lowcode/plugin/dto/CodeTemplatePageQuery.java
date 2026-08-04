package com.yutong.lowcode.plugin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 代码生成模板分页查询条件。
 * 支持 keyword/category/engineType/status 过滤
 */
@Getter
@Setter
public class CodeTemplatePageQuery {

    private int pageNo = 1;

    private int pageSize = 20;

    /** 模糊匹配 template_code 或 template_name */
    private String keyword;

    /** 模板分类 ENTITY/CONTROLLER/SERVICE/MAPPER/VUE_LIST/VUE_FORM/DDL 等 */
    private String category;

    /** FREEMARKER / VELOCITY */
    private String engineType;

    /** ACTIVE / INACTIVE */
    private String status;
}
