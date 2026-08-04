package com.yutong.sample.inventory.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 库存流水 (不可变)。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 * 任何库存变动都写入流水, 用于审计追溯。不提供 update/delete。
 * 验证能力: 事务一致性、审计追溯。
 */
@Getter
@Setter
@TableName("inv_stock_transaction")
public class InvStockTransaction extends BaseEntity {

    public static final String TYPE_IN = "IN";
    public static final String TYPE_OUT = "OUT";
    public static final String TYPE_COMPENSATE = "COMPENSATE";

    public static final String BIZ_INBOUND = "INBOUND";
    public static final String BIZ_OUTBOUND = "OUTBOUND";

    /** 流水号 TXyyyyMMddNNNNNN */
    private String transactionNo;

    private String balanceId;

    private String materialId;

    private String warehouseId;

    /** IN 入库 / OUT 出库 / COMPENSATE 补偿 (变动数量, 入库正数/出库负数/补偿正数) */
    private String transactionType;

    /** 变动数量 (IN 正 / OUT 负 / COMPENSATE 正) */
    private BigDecimal quantity;

    /** 变动前数量 */
    private BigDecimal quantityBefore;

    /** 变动后数量 */
    private BigDecimal quantityAfter;

    /** 关联业务单据类型 INBOUND/OUTBOUND */
    private String bizOrderType;

    private String bizOrderId;

    private String bizOrderNo;
}
