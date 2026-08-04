package com.yutong.sample.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 出库请求。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 * 幂等键 idempotencyKey 必填, 防止重复提交。库存不足时拦截 IVT-409001。
 */
@Data
public class OutboundRequest {

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 字")
    private String idempotencyKey;

    @NotBlank(message = "仓库 ID 不能为空")
    private String warehouseId;

    @NotBlank(message = "物料 ID 不能为空")
    private String materialId;

    @NotNull(message = "出库数量不能为空")
    private BigDecimal quantity;

    private BigDecimal unitCost;

    /** 出库类型 SALE/SCRAP/TRANSFER_OUT */
    private String outboundType;

    @Size(max = 64, message = "批次号不能超过 64 字")
    private String batchNo;

    @Size(max = 128, message = "客户不能超过 128 字")
    private String customer;
}
