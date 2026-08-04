package com.yutong.sample.ticket.service;

import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.sample.ticket.domain.WorkTicket;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * 工单领域服务 - 状态机。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 *
 * 状态机:
 *   NEW → ASSIGNED (ASSIGN 派单)
 *   ASSIGNED → PROCESSING (ACCEPT 接单)
 *   ASSIGNED → ASSIGNED (TRANSFER 转派)
 *   PROCESSING → SUSPENDED (SUSPEND 挂起)
 *   SUSPENDED → PROCESSING (RESUME 恢复)
 *   PROCESSING → COMPLETED (RESOLVE 完成)
 *   COMPLETED → PROCESSING (REOPEN 重开)
 *   COMPLETED → CLOSED (EVALUATE 评价后关闭 / CLOSE 直接关闭)
 *   NEW → CLOSED (CLOSE 直接关闭)
 */
@Service
public class TicketDomainService {

    /** 合法状态流转映射: action → {fromStatus → toStatus} */
    private static final Map<String, Map<String, String>> TRANSITIONS = Map.of(
            "ASSIGN", Map.of(WorkTicket.STATUS_NEW, WorkTicket.STATUS_ASSIGNED),
            "ACCEPT", Map.of(WorkTicket.STATUS_ASSIGNED, WorkTicket.STATUS_PROCESSING),
            "TRANSFER", Map.of(WorkTicket.STATUS_ASSIGNED, WorkTicket.STATUS_ASSIGNED,
                               WorkTicket.STATUS_PROCESSING, WorkTicket.STATUS_ASSIGNED),
            "SUSPEND", Map.of(WorkTicket.STATUS_PROCESSING, WorkTicket.STATUS_SUSPENDED),
            "RESUME", Map.of(WorkTicket.STATUS_SUSPENDED, WorkTicket.STATUS_PROCESSING),
            "RESOLVE", Map.of(WorkTicket.STATUS_PROCESSING, WorkTicket.STATUS_COMPLETED),
            "REOPEN", Map.of(WorkTicket.STATUS_COMPLETED, WorkTicket.STATUS_PROCESSING),
            "CLOSE", Map.of(WorkTicket.STATUS_NEW, WorkTicket.STATUS_CLOSED,
                            WorkTicket.STATUS_COMPLETED, WorkTicket.STATUS_CLOSED),
            "EVALUATE", Map.of(WorkTicket.STATUS_COMPLETED, WorkTicket.STATUS_CLOSED)
    );

    /**
     * 校验状态流转是否合法，返回目标状态。
     *
     * @param action 操作动作
     * @param fromStatus 当前状态
     * @return 目标状态
     * @throws BusinessConflictException 非法流转
     */
    public String validateTransition(String action, String fromStatus) {
        Map<String, String> actionMap = TRANSITIONS.get(action);
        if (actionMap == null) {
            throw new BusinessException(ErrorCode.WORK_TICKET_STATUS_NOT_ALLOWED,
                    "不支持的操作: " + action);
        }
        String toStatus = actionMap.get(fromStatus);
        if (toStatus == null) {
            throw new BusinessException(ErrorCode.WORK_TICKET_STATUS_NOT_ALLOWED,
                    String.format("工单状态 %s 不允许执行 %s 操作", fromStatus, action));
        }
        return toStatus;
    }

    /** 获取所有合法操作动作 */
    public Set<String> getValidActions() {
        return TRANSITIONS.keySet();
    }
}
