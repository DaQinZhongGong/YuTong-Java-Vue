package com.yutong.sample.inventory.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 库存余额 VO (含物料/仓库冗余信息)。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 */
@Data
public class StockBalanceVO {
    private String id;
    private String materialId;
    private String materialCode;
    private String materialName;
    private String warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private BigDecimal quantity;
    private BigDecimal lockedQuantity;
    private BigDecimal availableQuantity;
    private String lastInTime;
    private String lastOutTime;
}
