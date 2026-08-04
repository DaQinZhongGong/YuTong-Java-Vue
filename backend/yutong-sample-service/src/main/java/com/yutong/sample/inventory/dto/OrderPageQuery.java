package com.yutong.sample.inventory.dto;

import lombok.Data;

/**
 * 入库/出库单分页查询请求。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 */
@Data
public class OrderPageQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    private String orderNo;
    private String status;
    private String materialId;
    private String warehouseId;
    private String batchNo;
}
