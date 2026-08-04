package com.yutong.sample.inventory.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 出库单。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 * 状态机: DRAFT → CONFIRMED (扣减库存, 库存不足拦截) → COMPENSATED (异常补偿回补)
 * 验证能力: 并发扣减乐观锁、库存不足拦截、幂等键防重复、异常补偿。
 */
@Getter
@Setter
@TableName("inv_outbound_order")
public class InvOutboundOrder extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_COMPENSATED = "COMPENSATED";

    public static final String TYPE_SALE = "SALE";
    public static final String TYPE_SCRAP = "SCRAP";
    public static final String TYPE_TRANSFER_OUT = "TRANSFER_OUT";

    /** 单号 OUTyyyyMMddNNNN */
    private String orderNo;

    /** 幂等键 (租户内唯一) */
    private String idempotencyKey;

    private String warehouseId;

    private String materialId;

    private BigDecimal quantity;

    private BigDecimal unitCost;

    private BigDecimal totalAmount;

    /** 状态 DRAFT/CONFIRMED/COMPENSATED */
    private String status;

    /** 出库类型 SALE/SCRAP/TRANSFER_OUT */
    private String outboundType;

    private String batchNo;

    private String customer;

    private OffsetDateTime confirmedTime;

    private String confirmedBy;

    private OffsetDateTime compensatedTime;

    private String compensatedBy;

    /** 关联库存余额 ID (CONFIRMED 后回填) */
    private String balanceId;
}
