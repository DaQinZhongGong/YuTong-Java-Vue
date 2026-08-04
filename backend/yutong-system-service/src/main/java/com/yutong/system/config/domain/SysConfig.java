package com.yutong.system.config.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 参数配置。设计来源: 57-完整DDL清单 sys_config */
@Getter
@Setter
@TableName("sys_config")
public class SysConfig extends BaseEntity {
    @NotBlank
    @Size(max = 128)
    private String configKey;

    @Size(max = 4000)
    private String configValue;

    @NotBlank
    @Pattern(regexp = "STRING|NUMBER|BOOLEAN|JSON|SECRET_REF")
    private String valueType;

    private String configGroup;
    private Boolean editable;
    private Boolean sensitive;

    @NotBlank
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;
}
