package com.yutong.sample.mobile.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.sample.mobile.dto.MobileRequestDetailVO;
import com.yutong.sample.mobile.dto.MobileTodoVO;
import com.yutong.sample.request.domain.ApprovalRecord;
import com.yutong.sample.request.domain.BizRequest;
import com.yutong.sample.request.domain.BizRequestItem;
import com.yutong.sample.request.mapper.ApprovalRecordMapper;
import com.yutong.sample.request.mapper.BizRequestItemMapper;
import com.yutong.sample.request.mapper.BizRequestMapper;
import com.yutong.sample.request.service.BizRequestApplicationService;
import com.yutong.sample.request.service.BizRequestDomainService;
import com.yutong.system.message.domain.SysMessage;
import com.yutong.system.message.domain.SysTodoTask;
import com.yutong.system.message.mapper.SysMessageMapper;
import com.yutong.system.message.mapper.SysTodoTaskMapper;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 移动端聚合服务。设计来源: 18-样例业务详细设计
 * 聚合待办表(sys_todo_task)和申请单(biz_request)数据，为移动端提供轻量响应。
 */
@Service
public class MobileService {

    /** 待办资源编码，对齐 permissions.yaml system:todo:* 命名。 */
    public static final String TODO_RESOURCE_CODE = "system:todo";

    private static final Map<String, String> STATUS_LABELS = Map.of(
            BizRequest.STATUS_DRAFT, "草稿",
            BizRequest.STATUS_SUBMITTED, "待审核",
            BizRequest.STATUS_APPROVED, "已通过",
            BizRequest.STATUS_REJECTED, "已驳回",
            BizRequest.STATUS_ARCHIVED, "已归档"
    );

    private final SysTodoTaskMapper todoTaskMapper;
    private final SysMessageMapper messageMapper;
    private final BizRequestMapper bizRequestMapper;
    private final BizRequestItemMapper bizRequestItemMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final BizRequestApplicationService requestAppService;
    private final DataScopeResolver dataScopeResolver;

    public MobileService(SysTodoTaskMapper todoTaskMapper,
                         SysMessageMapper messageMapper,
                         BizRequestMapper bizRequestMapper,
                         BizRequestItemMapper bizRequestItemMapper,
                         ApprovalRecordMapper approvalRecordMapper,
                         BizRequestApplicationService requestAppService,
                         DataScopeResolver dataScopeResolver) {
        this.todoTaskMapper = todoTaskMapper;
        this.messageMapper = messageMapper;
        this.bizRequestMapper = bizRequestMapper;
        this.bizRequestItemMapper = bizRequestItemMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.requestAppService = requestAppService;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 移动端待办列表。聚合 sys_todo_task + biz_request 摘要。
     * GA2-DS: admin (ALL/TENANT) 可查看租户内全部待办; 非 admin 仅查看 assignee_id 匹配当前用户的待办。
     */
    public PageResult<MobileTodoVO> pageTodos(PageRequest request, String keyword, String todoStatus) {
        String userId = CurrentUserContext.getUserId();
        String tenantId = CurrentUserContext.getTenantId();
        DataScope scope = dataScopeResolver.resolve(TODO_RESOURCE_CODE);
        boolean canReadAll = scope != null
                && (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT);

        LambdaQueryWrapper<SysTodoTask> wrapper = new LambdaQueryWrapper<SysTodoTask>()
                .eq(SysTodoTask::getTenantId, tenantId)
                .eq(!canReadAll, SysTodoTask::getAssigneeId, userId)
                .eq(todoStatus != null && !todoStatus.isBlank(),
                        SysTodoTask::getTodoStatus, todoStatus != null ? todoStatus : "PENDING")
                .orderByDesc(SysTodoTask::getCreatedTime);
        Page<SysTodoTask> page = todoTaskMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);

        if (page.getRecords().isEmpty()) {
            return PageResult.of(List.of(), 0L, request.page(), request.size());
        }

        // 批量查询关联申请单
        List<String> bizIds = page.getRecords().stream()
                .map(SysTodoTask::getBizId)
                .distinct()
                .toList();
        Map<String, BizRequest> requestMap = bizRequestMapper.selectBatchIds(bizIds).stream()
                .collect(Collectors.toMap(BizRequest::getId, r -> r));

        List<MobileTodoVO> vos = page.getRecords().stream()
                .map(todo -> {
                    BizRequest req = requestMap.get(todo.getBizId());
                    if (req == null) return null;
                    return new MobileTodoVO(
                            todo.getId(),
                            todo.getBizType(),
                            todo.getBizId(),
                            req.getRequestNo(),
                            req.getTitle(),
                            req.getCustomerNameSnapshot(),
                            req.getTotalAmount(),
                            req.getSubmittedTime(),
                            STATUS_LABELS.getOrDefault(req.getRequestStatus(), req.getRequestStatus()),
                            req.getVersion()
                    );
                })
                .filter(vo -> vo != null)
                .filter(vo -> keyword == null || keyword.isBlank()
                        || (vo.requestNo() != null && vo.requestNo().contains(keyword))
                        || (vo.title() != null && vo.title().contains(keyword)))
                .toList();

        return PageResult.of(vos, page.getTotal(), request.page(), request.size());
    }

    /**
     * 移动端申请单详情。轻量字段 + 明细 + 审批记录。
     */
    public MobileRequestDetailVO getRequestDetail(String id) {
        BizRequest req = bizRequestMapper.selectById(id);
        if (req == null) {
            throw new ResourceNotFoundException(ErrorCode.BIZ_REQUEST_NOT_FOUND, "申请单不存在: " + id);
        }
        List<BizRequestItem> items = bizRequestItemMapper.selectList(
                new LambdaQueryWrapper<BizRequestItem>()
                        .eq(BizRequestItem::getRequestId, id)
                        .orderByAsc(BizRequestItem::getSortNo));
        List<ApprovalRecord> approvals = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getRequestId, id)
                        .orderByAsc(ApprovalRecord::getOperatedTime));

        return new MobileRequestDetailVO(
                req.getId(),
                req.getRequestNo(),
                req.getTitle(),
                req.getCustomerId(),
                req.getCustomerNameSnapshot(),
                req.getRequestStatus(),
                STATUS_LABELS.getOrDefault(req.getRequestStatus(), req.getRequestStatus()),
                req.getTotalAmount(),
                req.getApplyReason(),
                req.getApplicantNameSnapshot(),
                req.getSubmittedTime(),
                req.getApprovedTime(),
                req.getVersion(),
                items,
                approvals
        );
    }

    /**
     * 移动端审核通过。委托给申请单应用服务。
     */
    public void approve(String id, String opinion, Integer version, String idempotencyKey) {
        requestAppService.approve(id, opinion, version, idempotencyKey);
    }

    /**
     * 移动端驳回。委托给申请单应用服务。
     */
    public void reject(String id, String opinion, Integer version, String idempotencyKey) {
        requestAppService.reject(id, opinion, version, idempotencyKey);
    }

    /**
     * 移动端工作台统计。聚合待办数、未读消息数、待审核申请单数。
     */
    public Map<String, Object> getWorkbenchStats() {
        String userId = CurrentUserContext.getUserId();
        String tenantId = CurrentUserContext.getTenantId();

        Long todoCount = todoTaskMapper.selectCount(
                new LambdaQueryWrapper<SysTodoTask>()
                        .eq(SysTodoTask::getTenantId, tenantId)
                        .eq(SysTodoTask::getAssigneeId, userId)
                        .eq(SysTodoTask::getTodoStatus, "PENDING"));

        Long messageCount = messageMapper.selectCount(
                new LambdaQueryWrapper<SysMessage>()
                        .eq(SysMessage::getTenantId, tenantId)
                        .eq(SysMessage::getReceiverId, userId)
                        .eq(SysMessage::getReadStatus, "UNREAD"));

        Long pendingRequestCount = bizRequestMapper.selectCount(
                new LambdaQueryWrapper<BizRequest>()
                        .eq(BizRequest::getTenantId, tenantId)
                        .eq(BizRequest::getRequestStatus, BizRequest.STATUS_SUBMITTED));

        Map<String, Object> stats = new HashMap<>();
        stats.put("todoCount", todoCount);
        stats.put("messageCount", messageCount);
        stats.put("pendingRequestCount", pendingRequestCount);
        return stats;
    }
}
