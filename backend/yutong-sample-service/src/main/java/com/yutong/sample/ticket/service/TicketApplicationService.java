package com.yutong.sample.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.sample.ticket.domain.WorkTicket;
import com.yutong.sample.ticket.domain.WorkTicketCategory;
import com.yutong.sample.ticket.domain.WorkTicketLog;
import com.yutong.sample.ticket.dto.SaveTicketRequest;
import com.yutong.sample.ticket.dto.TicketActionRequest;
import com.yutong.sample.ticket.dto.TicketDetailVO;
import com.yutong.sample.ticket.mapper.WorkTicketCategoryMapper;
import com.yutong.sample.ticket.mapper.WorkTicketLogMapper;
import com.yutong.sample.ticket.mapper.WorkTicketMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 工单应用服务。设计来源: 35-样例业务矩阵扩展设计 P1 工单中心。
 * 事务编排: 创建、派单、接单、转单、挂起、恢复、完成、重开、关闭、评价。
 * 每次状态流转自动写入 work_ticket_log，形成处理时间线。
 * SLA 截止时间由分类 sla_hours 在创建时计算。
 */
@Service
public class TicketApplicationService {

    /** 工单资源编码，对齐 permissions.yaml biz:ticket:* 命名。 */
    public static final String RESOURCE_CODE = "biz:ticket";

    private final WorkTicketMapper ticketMapper;
    private final WorkTicketCategoryMapper categoryMapper;
    private final WorkTicketLogMapper logMapper;
    private final TicketSequenceService sequenceService;
    private final TicketDomainService domainService;
    private final DataScopeResolver dataScopeResolver;

    public TicketApplicationService(WorkTicketMapper ticketMapper,
                                    WorkTicketCategoryMapper categoryMapper,
                                    WorkTicketLogMapper logMapper,
                                    TicketSequenceService sequenceService,
                                    TicketDomainService domainService,
                                    DataScopeResolver dataScopeResolver) {
        this.ticketMapper = ticketMapper;
        this.categoryMapper = categoryMapper;
        this.logMapper = logMapper;
        this.sequenceService = sequenceService;
        this.domainService = domainService;
        this.dataScopeResolver = dataScopeResolver;
    }

    // ==================== 查询 ====================

    public PageResult<WorkTicket> pageTickets(PageRequest request, String ticketNo, String title,
                                               String status, String categoryId, String priority) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        QueryWrapper<WorkTicket> qw = new QueryWrapper<>();
        qw.eq("tenant_id", CurrentUserContext.getTenantId());
        if (ticketNo != null && !ticketNo.isBlank()) qw.like("ticket_no", ticketNo);
        if (title != null && !title.isBlank()) qw.like("title", title);
        if (status != null && !status.isBlank()) qw.eq("status", status);
        if (categoryId != null && !categoryId.isBlank()) qw.eq("category_id", categoryId);
        if (priority != null && !priority.isBlank()) qw.eq("priority", priority);
        qw.orderByDesc("created_time");
        // GA2-DS: 接入 DataScope 过滤，admin(ALL/TENANT) 放行，非 admin 按 created_by 过滤
        applyDataScope(qw, scope);
        Page<WorkTicket> page = ticketMapper.selectPage(
                new Page<>(request.page(), request.size()), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 QueryWrapper 追加 DataScope 过滤条件。
     * - ALL/TENANT: 无附加条件 (admin)
     * - 其它(SELF/DEPT/CUSTOM/NONE 等): created_by = currentUserId，userId 缺失时安全降级 1=0
     */
    private void applyDataScope(QueryWrapper<WorkTicket> qw, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            qw.apply("1 = 0");
            return;
        }
        qw.eq("created_by", userId);
    }

    public TicketDetailVO getTicketDetail(String id) {
        WorkTicket ticket = ticketMapper.selectById(id);
        if (ticket == null) {
            throw new ResourceNotFoundException(ErrorCode.WORK_TICKET_NOT_FOUND, "工单不存在: " + id);
        }
        List<WorkTicketLog> logs = logMapper.selectList(new LambdaQueryWrapper<WorkTicketLog>()
                .eq(WorkTicketLog::getTicketId, id)
                .orderByAsc(WorkTicketLog::getCreatedTime));
        TicketDetailVO vo = new TicketDetailVO();
        BeanUtils.copyProperties(ticket, vo);
        vo.setLogs(logs);
        return vo;
    }

    public List<WorkTicketCategory> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<WorkTicketCategory>()
                .eq(WorkTicketCategory::getStatus, WorkTicketCategory.STATUS_ACTIVE)
                .orderByAsc(WorkTicketCategory::getCategoryCode));
    }

    // ==================== 创建 ====================

    @Transactional
    public WorkTicket createTicket(SaveTicketRequest request) {
        // 校验分类
        WorkTicketCategory category = categoryMapper.selectById(request.getCategoryId());
        if (category == null || !WorkTicketCategory.STATUS_ACTIVE.equals(category.getStatus())) {
            throw new ResourceNotFoundException(ErrorCode.WORK_CATEGORY_NOT_FOUND,
                    "工单分类不存在或已禁用: " + request.getCategoryId());
        }

        WorkTicket ticket = new WorkTicket();
        ticket.setId(IdGenerator.nextId());
        ticket.setTicketNo(sequenceService.nextTicketNo());
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setCategoryId(category.getId());
        ticket.setCategoryNameSnapshot(category.getCategoryName());
        ticket.setPriority(request.getPriority() != null ? request.getPriority() : WorkTicket.PRIORITY_MEDIUM);
        ticket.setStatus(WorkTicket.STATUS_NEW);
        // 报告人默认当前用户
        String reporterId = request.getReporterId() != null ? request.getReporterId() : CurrentUserContext.getUserId();
        ticket.setReporterId(reporterId);
        ticket.setReporterNameSnapshot(CurrentUserContext.getUsername());
        ticket.setOwnerUserId(CurrentUserContext.getUserId());
        ticket.setOwnerDeptId(CurrentUserContext.getDeptId());
        ticket.setOwnerDeptPath(CurrentUserContext.getDeptPath());
        // SLA 截止时间 = now + category.slaHours
        ticket.setSlaDeadline(OffsetDateTime.now().plusHours(category.getSlaHours()));
        ticketMapper.insert(ticket);

        // 写入创建日志
        writeLog(ticket.getId(), "CREATE", null, WorkTicket.STATUS_NEW, "创建工单");
        return ticket;
    }

    // ==================== 状态流转 ====================

    @Transactional
    public WorkTicket doAction(String ticketId, TicketActionRequest request) {
        WorkTicket ticket = ticketMapper.selectById(ticketId);
        if (ticket == null) {
            throw new ResourceNotFoundException(ErrorCode.WORK_TICKET_NOT_FOUND, "工单不存在: " + ticketId);
        }

        String action = request.getAction();
        String fromStatus = ticket.getStatus();
        String toStatus = domainService.validateTransition(action, fromStatus);
        OffsetDateTime now = OffsetDateTime.now();

        // 各操作特有逻辑
        switch (action) {
            case "ASSIGN":
            case "TRANSFER":
                if (request.getHandlerId() == null || request.getHandlerId().isBlank()) {
                    throw new BusinessException(ErrorCode.WORK_TICKET_HANDLER_REQUIRED,
                            "派单/转单必须指定处理人");
                }
                ticket.setHandlerId(request.getHandlerId());
                ticket.setHandlerNameSnapshot(request.getHandlerName() != null
                        ? request.getHandlerName() : request.getHandlerId());
                if (ticket.getAssignedTime() == null) {
                    ticket.setAssignedTime(now);
                }
                break;
            case "ACCEPT":
                // 接单: handler 设为当前用户（若未指定）
                if (ticket.getHandlerId() == null) {
                    ticket.setHandlerId(CurrentUserContext.getUserId());
                    ticket.setHandlerNameSnapshot(CurrentUserContext.getUsername());
                }
                break;
            case "RESOLVE":
                ticket.setResolvedTime(now);
                break;
            case "CLOSE":
                ticket.setClosedTime(now);
                break;
            case "EVALUATE":
                if (request.getSatisfactionScore() == null
                        || request.getSatisfactionScore() < 1
                        || request.getSatisfactionScore() > 5) {
                    throw new BusinessException(ErrorCode.WORK_TICKET_EVALUATION_SCORE_INVALID,
                            "评价分数必须为 1-5");
                }
                ticket.setSatisfactionScore(request.getSatisfactionScore());
                ticket.setSatisfactionComment(request.getSatisfactionComment());
                ticket.setClosedTime(now);
                break;
            default:
                // SUSPEND/RESUME/REOPEN 无特殊处理
                break;
        }

        ticket.setStatus(toStatus);
        ticketMapper.updateById(ticket);

        writeLog(ticket.getId(), action, fromStatus, toStatus, request.getComment());
        return ticket;
    }

    // ==================== SLA 超时扫描 ====================

    /**
     * 查询已超时但未关闭的工单（SLA 定时扫描用）。
     * @return 超时工单列表
     */
    public List<WorkTicket> findOverdueTickets() {
        return ticketMapper.selectList(new LambdaQueryWrapper<WorkTicket>()
                .lt(WorkTicket::getSlaDeadline, OffsetDateTime.now())
                .in(WorkTicket::getStatus,
                        WorkTicket.STATUS_NEW, WorkTicket.STATUS_ASSIGNED,
                        WorkTicket.STATUS_PROCESSING, WorkTicket.STATUS_SUSPENDED));
    }

    // ==================== 私有方法 ====================

    private void writeLog(String ticketId, String action, String fromStatus, String toStatus, String comment) {
        WorkTicketLog log = new WorkTicketLog();
        log.setId(IdGenerator.nextId());
        log.setTicketId(ticketId);
        log.setAction(action);
        log.setFromStatus(fromStatus);
        log.setToStatus(toStatus);
        log.setOperatorId(CurrentUserContext.getUserId());
        log.setOperatorName(CurrentUserContext.getUsername());
        log.setComment(comment);
        logMapper.insert(log);
    }
}
