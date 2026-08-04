package com.yutong.system.dict.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 字典类型。设计来源: 57-完整DDL清单 sys_dict_type */
@Getter
@Setter
@TableName("sys_dict_type")
public class DictType extends BaseEntity {
    @NotBlank
    @Size(max = 64)
    private String dictType;

    @NotBlank
    @Size(max = 128)
    private String dictName;

    @NotBlank
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;

    private Boolean systemFlag;
    private Integer sortNo;
}
