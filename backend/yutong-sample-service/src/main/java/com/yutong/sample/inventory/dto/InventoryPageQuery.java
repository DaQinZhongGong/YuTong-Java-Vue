package com.yutong.sample.inventory.dto;

import lombok.Data;

/**
 * 库存余额分页查询请求。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 */
@Data
public class InventoryPageQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String materialId;
    private String warehouseId;
    private String materialCode;
    private String materialName;
}
