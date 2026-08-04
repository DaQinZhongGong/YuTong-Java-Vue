package com.yutong.lowcode.meta.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 组件协议元数据分页查询条件。设计来源: 36-低代码高级能力设计 组件协议 API 契约
 * 支持 category / platform / status / keyword（模糊匹配 component_code 或 component_name）过滤
 */
@Getter
@Setter
public class ComponentRegistryPageQuery {

    private int pageNo = 1;

    private int pageSize = 20;

    /** INPUT / DISPLAY / CONTAINER / TABLE / BUSINESS / MOBILE / CHART */
    private String category;

    /** WEB / MOBILE / BOTH */
    private String platform;

    /** DRAFT / PUBLISHED / DISABLED */
    private String status;

    /** 模糊匹配 component_code 或 component_name */
    private String keyword;
}
