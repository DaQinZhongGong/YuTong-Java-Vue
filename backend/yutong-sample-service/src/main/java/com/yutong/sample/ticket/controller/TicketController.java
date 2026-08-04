package com.yutong.sample.ticket.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.sample.ticket.domain.WorkTicket;
import com.yutong.sample.ticket.domain.WorkTicketCategory;
import com.yutong.sample.ticket.dto.SaveTicketRequest;
import com.yutong.sample.ticket.dto.TicketActionRequest;
import com.yutong.sample.ticket.dto.TicketDetailVO;
import com.yutong.sample.ticket.service.TicketApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工单 Controller。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 * 提供工单 CRUD + 状态流转 + 分类查询 + 评价 API。
 */
@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "Ticket", description = "工单中心")
public class TicketController {

    private final TicketApplicationService ticketService;

    public TicketController(TicketApplicationService ticketService) {
        this.ticketService = ticketService;
    }

    @GetMapping
    @Operation(summary = "分页查询工单列表", operationId = "pageTickets")
    @RequiresPermission("biz:ticket:list")
    public Result<PageResult<WorkTicket>> pageTickets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String ticketNo,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String priority) {
        return Result.ok(ticketService.pageTickets(
                PageRequest.of(page, size), ticketNo, title, status, categoryId, priority));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询工单详情（含处理记录时间线）", operationId = "getTicketDetail")
    @RequiresPermission("biz:ticket:detail")
    public Result<TicketDetailVO> getTicketDetail(@PathVariable String id) {
        return Result.ok(ticketService.getTicketDetail(id));
    }

    @PostMapping
    @Operation(summary = "创建工单", operationId = "createTicket")
    @RequiresPermission("biz:ticket:create")
    public Result<WorkTicket> createTicket(@Valid @RequestBody SaveTicketRequest request) {
        return Result.ok(ticketService.createTicket(request));
    }

    @PostMapping("/{id}/actions")
    @Operation(summary = "工单状态流转操作", operationId = "ticketAction",
            description = "action: ASSIGN 派单 / ACCEPT 接单 / TRANSFER 转单 / SUSPEND 挂起 / RESUME 恢复 / RESOLVE 完成 / REOPEN 重开 / CLOSE 关闭 / EVALUATE 评价")
    @RequiresPermission("biz:ticket:create")
    public Result<WorkTicket> doAction(@PathVariable String id, @RequestBody TicketActionRequest request) {
        return Result.ok(ticketService.doAction(id, request));
    }

    @GetMapping("/categories")
    @Operation(summary = "查询工单分类列表", operationId = "listTicketCategories")
    @RequiresPermission("biz:ticket:list")
    public Result<List<WorkTicketCategory>> listCategories() {
        return Result.ok(ticketService.listCategories());
    }
}
