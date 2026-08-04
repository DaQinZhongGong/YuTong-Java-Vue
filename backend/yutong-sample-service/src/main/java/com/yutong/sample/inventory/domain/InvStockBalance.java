package com.yutong.sample.inventory.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 库存余额表 (物料+仓库 维度唯一)。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 * 验证能力: 并发扣减乐观锁、库存余额唯一约束。
 * 可用数量 = quantity - lockedQuantity (应用层计算, 不存为列避免脏读)。
 */
@Getter
@Setter
@TableName("inv_stock_balance")
public class InvStockBalance extends BaseEntity {

    private String materialId;

    private String warehouseId;

    /** 库存数量 */
    private BigDecimal quantity;

    /** 锁定数量 (预留) */
    private BigDecimal lockedQuantity;

    private OffsetDateTime lastInTime;

    private OffsetDateTime lastOutTime;

    /** 计算可用数量 */
    public BigDecimal getAvailableQuantity() {
        BigDecimal q = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal l = lockedQuantity == null ? BigDecimal.ZERO : lockedQuantity;
        return q.subtract(l);
    }
}
