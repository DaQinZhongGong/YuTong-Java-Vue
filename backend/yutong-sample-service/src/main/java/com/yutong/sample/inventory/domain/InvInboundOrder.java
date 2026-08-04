package com.yutong.sample.inventory.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 入库单。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 * 状态机: DRAFT → CONFIRMED (不可逆, 触发库存增加)
 * 验证能力: 幂等键防重复提交、状态流转、库存增加。
 */
@Getter
@Setter
@TableName("inv_inbound_order")
public class InvInboundOrder extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";

    public static final String TYPE_PURCHASE = "PURCHASE";
    public static final String TYPE_RETURN = "RETURN";
    public static final String TYPE_TRANSFER_IN = "TRANSFER_IN";
    public static final String TYPE_INITIAL = "INITIAL";

    /** 单号 INyyyyMMddNNNN */
    private String orderNo;

    /** 幂等键 (租户内唯一) */
    private String idempotencyKey;

    private String warehouseId;

    private String materialId;

    private BigDecimal quantity;

    private BigDecimal unitCost;

    private BigDecimal totalAmount;

    /** 状态 DRAFT/CONFIRMED */
    private String status;

    /** 入库类型 PURCHASE/RETURN/TRANSFER_IN/INITIAL */
    private String inboundType;

    private String batchNo;

    private String supplier;

    private OffsetDateTime confirmedTime;

    private String confirmedBy;

    /** 关联库存余额 ID (CONFIRMED 后回填) */
    private String balanceId;
}
