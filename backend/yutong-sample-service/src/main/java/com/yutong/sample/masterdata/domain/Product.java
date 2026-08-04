package com.yutong.sample.masterdata.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/** 商品主数据。设计来源: 18-样例业务详细设计、57-完整DDL清单 biz_product */
@Getter
@Setter
@TableName("biz_product")
public class Product extends BaseEntity {

    /** 商品编码 (租户内唯一) */
    @NotBlank
    @Size(max = 32)
    private String productCode;

    /** 商品名称 */
    @NotBlank
    @Size(max = 128)
    private String productName;

    /** 单位 */
    @Size(max = 32)
    private String unit;

    /** 单价 */
    private BigDecimal price;

    /** 状态: ENABLED / DISABLED */
    @NotBlank
    @Pattern(regexp = "ENABLED|DISABLED")
    private String status;
}
