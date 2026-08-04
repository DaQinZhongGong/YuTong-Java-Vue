package com.yutong.sample.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 入库请求。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 * 幂等键 idempotencyKey 必填, 防止重复提交。
 */
@Data
public class InboundRequest {

    @NotBlank(message = "幂等键不能为空")
    @Size(max = 64, message = "幂等键不能超过 64 字")
    private String idempotencyKey;

    @NotBlank(message = "仓库 ID 不能为空")
    private String warehouseId;

    @NotBlank(message = "物料 ID 不能为空")
    private String materialId;

    @NotNull(message = "入库数量不能为空")
    private BigDecimal quantity;

    private BigDecimal unitCost;

    /** 入库类型 PURCHASE/RETURN/TRANSFER_IN/INITIAL */
    private String inboundType;

    @Size(max = 64, message = "批次号不能超过 64 字")
    private String batchNo;

    @Size(max = 128, message = "供应商不能超过 128 字")
    private String supplier;
}
