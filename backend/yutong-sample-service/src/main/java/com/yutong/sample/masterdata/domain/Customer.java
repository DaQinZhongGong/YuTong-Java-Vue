package com.yutong.sample.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 客户主数据。设计来源: 18-样例业务详细设计、57-完整DDL清单 biz_customer */
@Getter
@Setter
@TableName("biz_customer")
public class Customer extends BaseEntity {

    /** 客户编码 (租户内唯一) */
    @NotBlank
    @Size(max = 32)
    private String customerCode;

    /** 客户名称 */
    @NotBlank
    @Size(max = 128)
    private String customerName;

    /** 联系人 */
    @Size(max = 64)
    private String contactName;

    /** 联系电话 */
    @Size(max = 20)
    private String contactPhone;

    /** 地址 */
    @Size(max = 256)
    private String address;

    /** 状态: ENABLED / DISABLED */
    @NotBlank
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;
}
