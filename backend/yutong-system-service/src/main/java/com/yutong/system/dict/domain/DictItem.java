package com.yutong.system.dict.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 字典项。设计来源: 57-完整DDL清单 sys_dict_item */
@Getter
@Setter
@TableName("sys_dict_item")
public class DictItem extends BaseEntity {
    private String dictType;
    private String itemCode;
    private String itemLabel;
    private String itemLabelI18n;
    private String itemValue;
    private String status;
    private Integer sortNo;
    private String colorToken;
}
