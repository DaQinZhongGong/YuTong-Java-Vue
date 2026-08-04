package com.yutong.sample.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 新建/更新物料请求。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 */
@Data
public class SaveMaterialRequest {

    @NotBlank(message = "物料编码不能为空")
    @Size(max = 64, message = "物料编码不能超过 64 字")
    private String materialCode;

    @NotBlank(message = "物料名称不能为空")
    @Size(max = 128, message = "物料名称不能超过 128 字")
    private String materialName;

    @Size(max = 32, message = "物料类型不能超过 32 字")
    private String materialType;

    @Size(max = 128, message = "规格不能超过 128 字")
    private String spec;

    @Size(max = 32, message = "单位不能超过 32 字")
    private String unit;

    @Size(max = 64, message = "分类不能超过 64 字")
    private String category;

    @Size(max = 64, message = "条码不能超过 64 字")
    private String barcode;

    private BigDecimal referencePrice;
}
