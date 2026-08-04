package com.yutong.sample.inventory.service;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.sample.inventory.domain.InvInboundOrder;
import com.yutong.sample.inventory.domain.InvOutboundOrder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 库存领域服务 - 状态机与并发控制。设计来源: 35-样例业务矩阵扩展设计 P1 库存出入库。
 *
 * <p>入库单状态机:
 * <pre>
 *   DRAFT → CONFIRMED (CONFIRM 确认入库, 触发库存增加, 不可逆)
 * </pre>
 *
 * <p>出库单状态机:
 * <pre>
 *   DRAFT     → CONFIRMED   (CONFIRM 确认出库, 触发库存扣减, 库存不足拦截)
 *   CONFIRMED → COMPENSATED (COMPENSATE 异常补偿, 库存回补)
 * </pre>
 */
@Service
public class InventoryDomainService {

    /** 入库单合法状态流转 */
    private static final Map<String, String> INBOUND_TRANSITIONS = Map.of(
            "CONFIRM", InvInboundOrder.STATUS_DRAFT
    );
    private static final Map<String, String> INBOUND_TO_STATUS = Map.of(
            "CONFIRM", InvInboundOrder.STATUS_CONFIRMED
    );

    /** 出库单合法状态流转 */
    private static final Map<String, Set<String>> OUTBOUND_TRANSITIONS = Map.of(
            "CONFIRM", Set.of(InvOutboundOrder.STATUS_DRAFT),
            "COMPENSATE", Set.of(InvOutboundOrder.STATUS_CONFIRMED)
    );
    private static final Map<String, String> OUTBOUND_TO_STATUS = Map.of(
            "CONFIRM", InvOutboundOrder.STATUS_CONFIRMED,
            "COMPENSATE", InvOutboundOrder.STATUS_COMPENSATED
    );

    public String validateInboundTransition(String action, String fromStatus) {
        String requiredFrom = INBOUND_TRANSITIONS.get(action);
        if (requiredFrom == null || !requiredFrom.equals(fromStatus)) {
            throw new BusinessException(ErrorCode.IVT_STATUS_NOT_ALLOWED,
                    "入库单当前状态不支持该操作: action=" + action + ", status=" + fromStatus);
        }
        return INBOUND_TO_STATUS.get(action);
    }

    public String validateOutboundTransition(String action, String fromStatus) {
        Set<String> allowedFrom = OUTBOUND_TRANSITIONS.get(action);
        if (allowedFrom == null || !allowedFrom.contains(fromStatus)) {
            throw new BusinessException(ErrorCode.IVT_STATUS_NOT_ALLOWED,
                    "出库单当前状态不支持该操作: action=" + action + ", status=" + fromStatus);
        }
        return OUTBOUND_TO_STATUS.get(action);
    }

    /** 校验数量必须 > 0 */
    public void validateQuantity(java.math.BigDecimal quantity) {
        if (quantity == null || quantity.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.IVT_QUANTITY_INVALID,
                    "数量必须大于 0");
        }
    }
}
