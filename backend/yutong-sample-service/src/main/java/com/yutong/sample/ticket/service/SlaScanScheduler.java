package com.yutong.sample.ticket.service;

import com.yutong.sample.ticket.domain.WorkTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SLA 超时扫描定时任务。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 * <p>
 * 每小时扫描一次超时未关闭的工单，记录日志。
 * 后续可扩展为发送消息通知（通过 MessageFacade）或自动升级优先级。
 */
@Component
public class SlaScanScheduler {

    private static final Logger log = LoggerFactory.getLogger(SlaScanScheduler.class);

    private final TicketApplicationService ticketService;

    public SlaScanScheduler(TicketApplicationService ticketService) {
        this.ticketService = ticketService;
    }

    /**
     * 每小时扫描 SLA 超时工单。
     * cron: 0 0 * * * *（每整点执行）
     */
    @Scheduled(cron = "0 0 * * * *")
    public void scanOverdueTickets() {
        List<WorkTicket> overdue = ticketService.findOverdueTickets();
        if (!overdue.isEmpty()) {
            log.warn("[SLA扫描] 发现 {} 张超时工单:", overdue.size());
            for (WorkTicket t : overdue) {
                log.warn("  工单 {} | 标题: {} | 状态: {} | SLA截止: {} | 处理人: {}",
                        t.getTicketNo(), t.getTitle(), t.getStatus(),
                        t.getSlaDeadline(), t.getHandlerNameSnapshot());
            }
            // TODO: 后续通过 MessageFacade 发送超时通知给处理人/管理员
        } else {
            log.debug("[SLA扫描] 无超时工单");
        }
    }
}
