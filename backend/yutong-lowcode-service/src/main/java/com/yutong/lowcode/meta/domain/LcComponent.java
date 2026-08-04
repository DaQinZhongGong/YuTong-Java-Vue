package com.yutong.lowcode.meta.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 低代码页面组件。设计来源: 14-低代码平台设计、57-完整DDL清单 lc_component
 * 自定义组件必须声明标准 Props: value/modelValue, field, disabled, formMode
 */
@Getter
@Setter
@TableName("lc_component")
public class LcComponent extends BaseEntity {

    private String pageId;

    private String componentCode;

    /** INPUT / SELECT / TABLE / FORM / BUTTON / CUSTOM */
    private String componentType;

    /** 组件属性 JSON */
    private String propsJson;

    /** 校验/联动规则 JSON */
    private String rulesJson;

    /** 事件绑定 JSON，命名: field:{code}:change, action:{code}:trigger */
    private String eventsJson;

    private String propsSchemaVersion;

    private String parentComponentId;

    private Integer sortNo;
}
