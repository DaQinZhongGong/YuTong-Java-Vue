package com.yutong.sample.request.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.event.BizRequestStateChangedEvent;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.DataScopeDeniedException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.trace.TraceContext;
import com.yutong.infra.persistence.DataScopeFilter;
import com.yutong.sample.masterdata.domain.Customer;
import com.yutong.sample.masterdata.domain.Product;
import com.yutong.sample.masterdata.mapper.CustomerMapper;
import com.yutong.sample.masterdata.mapper.ProductMapper;
import com.yutong.sample.request.domain.ApprovalRecord;
import com.yutong.sample.request.domain.BizRequest;
import com.yutong.sample.request.domain.BizRequestItem;
import com.yutong.sample.request.dto.BizRequestDetailVO;
import com.yutong.sample.request.dto.SaveBizRequestRequest;
import com.yutong.sample.request.mapper.ApprovalRecordMapper;
import com.yutong.sample.request.mapper.BizRequestItemMapper;
import com.yutong.sample.request.mapper.BizRequestMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 申请单应用服务。设计来源: 18-样例业务详细设计、67-数据权限与审计日志详设
 * 事务编排: 草稿保存、提交、审核、驳回、撤回、归档。
 * 金额由后端重新计算；明细采用"全删全插"策略；写操作均 @Transactional。
 *
 * GA2-02 扩展: 接入 DataScopeResolver，对 pageRequests/getRequestDetail/状态流转强制数据权限校验。
 * resourceCode = "biz:request"，由后端决定，不接受前端传入。
 * 越权访问抛 DataScopeDeniedException (403 AUTH-403002)。
 */
@Service
public class BizRequestApplicationService {

    /** 申请单资源编码，对齐 67 号文档第 56-69 行核心表 DataScope 映射矩阵。 */
    public static final String RESOURCE_CODE = "biz:request";

    private final BizRequestMapper bizRequestMapper;
    private final BizRequestItemMapper bizRequestItemMapper;
    private final ApprovalRecordMapper approvalRecordMapper;
    private final SequenceService sequenceService;
    private final BizRequestDomainService domainService;
    private final DataScopeResolver dataScopeResolver;
    /** GA2-32: 申请单状态变更后发布事件，由 system-service 监听推送实时信封。 */
    private final ApplicationEventPublisher eventPublisher;
    /** GA2-L191: 客户/商品主数据 Mapper，用于反查快照 (customer_name_snapshot / product_code_snapshot / product_name_snapshot)。
     *  快照字段语义为"防更名后展示错误"，必须由服务端根据 customerId/productId 反查主数据填充，不能由前端传入。 */
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;

    public BizRequestApplicationService(BizRequestMapper bizRequestMapper,
                                       BizRequestItemMapper bizRequestItemMapper,
                                       ApprovalRecordMapper approvalRecordMapper,
                                       SequenceService sequenceService,
                                       BizRequestDomainService domainService,
                                       DataScopeResolver dataScopeResolver,
                                       ApplicationEventPublisher eventPublisher,
                                       CustomerMapper customerMapper,
                                       ProductMapper productMapper) {
        this.bizRequestMapper = bizRequestMapper;
        this.bizRequestItemMapper = bizRequestItemMapper;
        this.approvalRecordMapper = approvalRecordMapper;
        this.sequenceService = sequenceService;
        this.domainService = domainService;
        this.dataScopeResolver = dataScopeResolver;
        this.eventPublisher = eventPublisher;
        this.customerMapper = customerMapper;
        this.productMapper = productMapper;
    }

    // ==================== 查询 ====================

    /**
     * 分页查询申请单列表。
     * GA2-02: 强制应用 DataScope 过滤，NONE/SELF/DEPT/DEPT_AND_CHILD/CUSTOM 全部走 DataScopeFilter。
     */
    public PageResult<BizRequest> pageRequests(PageRequest request, String requestNo, String title,
                                                String status, String customerId) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        QueryWrapper<BizRequest> queryWrapper = new QueryWrapper<>();
        // tenant_id 先过滤（67 号文档第 23 行: 必须先 tenant_id 与 deleted=false）
        queryWrapper.eq("tenant_id", CurrentUserContext.getTenantId());
        // DataScope 过滤追加
        DataScopeFilter.apply(queryWrapper, scope);
        // 业务过滤条件
        if (requestNo != null && !requestNo.isBlank()) {
            queryWrapper.like("request_no", requestNo);
        }
        if (title != null && !title.isBlank()) {
            queryWrapper.like("title", title);
        }
        if (status != null && !status.isBlank()) {
            queryWrapper.eq("request_status", status);
        }
        if (customerId != null && !customerId.isBlank()) {
            queryWrapper.eq("customer_id", customerId);
        }
        queryWrapper.orderByDesc("created_time");
        Page<BizRequest> page = bizRequestMapper.selectPage(
                new Page<>(request.page(), request.size()), queryWrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    // ==================== keyset pagination (PERF-002) ====================

    /**
     * keyset pagination 分页查询申请单列表 (PERF-002 调优)。
     *
     * <p>设计来源: 72-性能容量规划与压测方案详设 (调优策略: 索引、覆盖查询)。
     * <p>问题: OFFSET 分页深翻页时 (如第 1000 页) 性能急剧下降，
     *       因为数据库需要扫描并丢弃前 N 条记录，时间复杂度 O(N)。
     * <p>优化: 使用游标 (cursor) 替代 OFFSET，WHERE created_time &lt; cursor_time
     *       直接定位起始位置，走索引时间复杂度 O(log N)，深翻页性能稳定。
     *
     * <p>cursor 格式: Base64(created_time_iso8601 + "|" + id)
     * <p>响应: total=-1 表示 keyset 模式无精确总数，nextCursor 为下一页游标 (null 表示已到末尾)。
     * 前端用 records.size() &lt; size 或 nextCursor=null 判断是否到底。
     *
     * <p>兼容性: 与 {@link #pageRequests} 的业务过滤条件 (requestNo/title/status/customerId) 完全一致，
     * DataScope 过滤也完全一致，确保权限安全。
     *
     * @param cursor 游标 (null 或空时查询第一页)
     * @param size   每页大小 (1-500)
     */
    public PageResult<BizRequest> pageRequestsByKeyset(String cursor, int size,
                                                        String requestNo, String title,
                                                        String status, String customerId) {
        // 参数保护
        int safeSize = Math.max(1, Math.min(size, 500));
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        QueryWrapper<BizRequest> wrapper = new QueryWrapper<>();
        // tenant_id 先过滤（67 号文档硬约束）
        wrapper.eq("tenant_id", CurrentUserContext.getTenantId());
        DataScopeFilter.apply(wrapper, scope);

        // keyset 条件: WHERE created_time < cursor_time OR (created_time = cursor_time AND id < cursor_id)
        // 用 AND(OR(...)) 包裹，确保不影响其他过滤条件
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = decodeCursor(cursor);
            OffsetDateTime cursorTime = OffsetDateTime.parse(parts[0], DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            String cursorId = parts[1];
            wrapper.and(w -> w
                    .lt("created_time", cursorTime)
                    .or(o -> o.eq("created_time", cursorTime).lt("id", cursorId)));
        }

        // 业务过滤条件 (与 pageRequests 完全一致)
        if (requestNo != null && !requestNo.isBlank()) {
            wrapper.like("request_no", requestNo);
        }
        if (title != null && !title.isBlank()) {
            wrapper.like("title", title);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("request_status", status);
        }
        if (customerId != null && !customerId.isBlank()) {
            wrapper.eq("customer_id", customerId);
        }

        // 排序: created_time DESC, id DESC (与索引 idx_biz_request_keyset_time 对齐)
        wrapper.orderByDesc("created_time").orderByDesc("id");
        // 多查一条判断是否有下一页 (LIMIT size+1)
        wrapper.last("LIMIT " + (safeSize + 1));

        List<BizRequest> records = bizRequestMapper.selectList(wrapper);
        boolean hasMore = records.size() > safeSize;
        if (hasMore) {
            records = records.subList(0, safeSize);
        }

        // 生成下一页游标
        String nextCursor = null;
        if (hasMore && !records.isEmpty()) {
            BizRequest last = records.get(records.size() - 1);
            nextCursor = encodeCursor(last.getCreatedTime(), last.getId());
        }

        return PageResult.ofKeyset(records, 1, safeSize, nextCursor);
    }

    /**
     * 编码 keyset 游标: Base64(created_time_iso8601 + "|" + id)。
     */
    private String encodeCursor(OffsetDateTime createdTime, String id) {
        String raw = createdTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) + "|" + id;
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 解码 keyset 游标，返回 [createdTime, id]。
     * 解码失败抛 BusinessConflictException (400)。
     */
    private String[] decodeCursor(String cursor) {
        try {
            byte[] decoded = Base64.getDecoder().decode(cursor);
            String raw = new String(decoded, StandardCharsets.UTF_8);
            int sep = raw.indexOf('|');
            if (sep <= 0 || sep >= raw.length() - 1) {
                throw new IllegalArgumentException("cursor 格式无效");
            }
            return new String[]{raw.substring(0, sep), raw.substring(sep + 1)};
        } catch (IllegalArgumentException e) {
            throw new BusinessConflictException("游标格式无效: " + cursor);
        }
    }

    /**
     * 查询申请单详情 (主表 + 明细 + 审批记录)。
     * GA2-02: 强制校验 DataScope，越权抛 DataScopeDeniedException。
     * GA2-03-4: 基于 DataScope.includeSensitive 对敏感字段脱敏 (TC-SEC-DATA-002)。
     *           viewer 用户 includeSensitive=false，customerNameSnapshot/applicantNameSnapshot/applyReason 被脱敏。
     */
    public BizRequestDetailVO getRequestDetail(String id) {
        BizRequest request = bizRequestMapper.selectById(id);
        if (request == null) {
            throw new ResourceNotFoundException(ErrorCode.BIZ_REQUEST_NOT_FOUND, "申请单不存在: " + id);
        }
        // DataScope 校验
        checkDataScope(request);
        List<BizRequestItem> items = bizRequestItemMapper.selectList(
                new LambdaQueryWrapper<BizRequestItem>()
                        .eq(BizRequestItem::getRequestId, id)
                        .orderByAsc(BizRequestItem::getSortNo));
        List<ApprovalRecord> approvals = approvalRecordMapper.selectList(
                new LambdaQueryWrapper<ApprovalRecord>()
                        .eq(ApprovalRecord::getRequestId, id)
                        .orderByAsc(ApprovalRecord::getOperatedTime));

        BizRequestDetailVO vo = new BizRequestDetailVO();
        BeanUtils.copyProperties(request, vo);
        vo.setItems(items);
        vo.setApprovals(approvals);
        // GA2-03-4: 敏感字段脱敏 (TC-SEC-DATA-002)
        applySensitiveMasking(vo, dataScopeResolver.resolve(RESOURCE_CODE));
        return vo;
    }

    /**
     * GA2-03-4: 基于 DataScope 对申请单敏感字段脱敏。
     * 设计来源: 64-安全威胁模型 TC-SEC-DATA-002、67-数据权限与审计日志详设 includeSensitive 字段。
     * 规则: 若 scope.canViewSensitive() == false，对 customerNameSnapshot/applicantNameSnapshot/applyReason 应用掩码。
     * 敏感字段集合定义在此处，作为申请单领域知识的唯一来源。
     */
    private static final String SENSITIVE_MASK = "***";
    private void applySensitiveMasking(BizRequestDetailVO vo, DataScope scope) {
        if (scope == null || !scope.canViewSensitive()) {
            if (vo.getCustomerNameSnapshot() != null) {
                vo.setCustomerNameSnapshot(SENSITIVE_MASK);
            }
            if (vo.getApplicantNameSnapshot() != null) {
                vo.setApplicantNameSnapshot(SENSITIVE_MASK);
            }
            if (vo.getApplyReason() != null) {
                vo.setApplyReason(SENSITIVE_MASK);
            }
        }
    }

    // ==================== 保存草稿 ====================

    /**
     * 新建或保存草稿。
     * 新建: 生成 requestNo、applicant_id=当前用户、status=DRAFT。
     * 修改: 校验状态为 DRAFT 或 REJECTED，REJECTED 编辑后回到 DRAFT。
     * 明细采用"全删全插"策略；total_amount 由后端按明细重新计算。
     *
     * GA2-02: 修改草稿时强制校验 DataScope（越权编辑抛 DataScopeDeniedException）。
     */
    @Transactional
    public BizRequest saveDraft(SaveBizRequestRequest request) {
        List<SaveBizRequestRequest.Item> items = request.getItems();
        BigDecimal totalAmount = calculateTotalAmount(items);

        if (request.getId() == null || request.getId().isBlank()) {
            // ===== 新建 =====
            BizRequest entity = new BizRequest();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(CurrentUserContext.getTenantId());
            entity.setCreatedBy(CurrentUserContext.getUserId());
            entity.setRequestNo(sequenceService.nextRequestNo());
            entity.setTitle(request.getTitle());
            entity.setCustomerId(request.getCustomerId());
            entity.setCustomerNameSnapshot(resolveCustomerName(request.getCustomerId(), request.getCustomerNameSnapshot()));
            entity.setApplyReason(request.getApplyReason());
            entity.setRequestStatus(BizRequest.STATUS_DRAFT);
            entity.setTotalAmount(totalAmount);
            entity.setApplicantId(CurrentUserContext.getUserId());
            entity.setApplicantNameSnapshot(CurrentUserContext.getUsername());
            entity.setOwnerUserId(CurrentUserContext.getUserId());
            // dept 字段从 CurrentUserContext 透传（GA2-02）
            entity.setOwnerDeptId(CurrentUserContext.getDeptId());
            entity.setOwnerDeptPath(CurrentUserContext.getDeptPath());
            bizRequestMapper.insert(entity);
            saveItems(entity.getId(), items);
            return entity;
        }

        // ===== 修改 =====
        BizRequest existing = bizRequestMapper.selectById(request.getId());
        if (existing == null) {
            throw new ResourceNotFoundException(ErrorCode.BIZ_REQUEST_NOT_FOUND, "申请单不存在: " + request.getId());
        }
        // GA2-02: 修改草稿强制校验 DataScope
        checkDataScope(existing);
        // 乐观锁版本检查
        checkVersion(request.getVersion(), existing.getVersion());
        // 状态校验: 仅 DRAFT 或 REJECTED 可编辑
        if (!BizRequest.STATUS_DRAFT.equals(existing.getRequestStatus())
                && !BizRequest.STATUS_REJECTED.equals(existing.getRequestStatus())) {
            throw new BusinessConflictException(
                    "申请单当前状态[" + existing.getRequestStatus() + "]不允许编辑");
        }
        existing.setTitle(request.getTitle());
        existing.setCustomerId(request.getCustomerId());
        existing.setCustomerNameSnapshot(resolveCustomerName(request.getCustomerId(), request.getCustomerNameSnapshot()));
        existing.setApplyReason(request.getApplyReason());
        existing.setTotalAmount(totalAmount);
        // REJECTED 编辑后回到 DRAFT
        existing.setRequestStatus(BizRequest.STATUS_DRAFT);
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = bizRequestMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        // 明细: 全删全插
        saveItems(existing.getId(), items);
        return existing;
    }

    // ==================== 删除 ====================

    /**
     * 删除申请单 (guardedDelete 策略)。
     * 仅校验存在性 + DataScope，逻辑删除主表与明细。
     * 已删除记录 selectById 返回 null → 抛 404 (自然幂等)。
     *
     * GA2-L189: 补齐 delete 方法，对齐 operation-policies.yaml deleteBizRequest → guardedDelete profile。
     */
    @Transactional
    public void delete(String id) {
        BizRequest existing = bizRequestMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException(ErrorCode.BIZ_REQUEST_NOT_FOUND, "申请单不存在: " + id);
        }
        checkDataScope(existing);
        bizRequestMapper.deleteById(id);
        bizRequestItemMapper.delete(new LambdaQueryWrapper<BizRequestItem>()
                .eq(BizRequestItem::getRequestId, id));
    }

    // ==================== 状态流转 ====================

    /**
     * 提交: DRAFT/REJECTED → SUBMITTED。设置 submitted_time，写审批记录(SUBMIT)。
     */
    @Transactional
    public BizRequest submit(String id, Integer version, String idempotencyKey) {
        return doTransition(id, version, ApprovalRecord.ACTION_SUBMIT, null, null,
                r -> r.setSubmittedTime(OffsetDateTime.now()));
    }

    /**
     * 审核通过: SUBMITTED → APPROVED。设置 approved_time，写审批记录(APPROVE, APPROVED)。
     */
    @Transactional
    public BizRequest approve(String id, String opinion, Integer version, String idempotencyKey) {
        return doTransition(id, version, ApprovalRecord.ACTION_APPROVE,
                ApprovalRecord.RESULT_APPROVED, opinion,
                r -> r.setApprovedTime(OffsetDateTime.now()));
    }

    /**
     * 驳回: SUBMITTED → REJECTED。opinion 必填，写审批记录(REJECT, REJECTED)。
     */
    @Transactional
    public BizRequest reject(String id, String opinion, Integer version, String idempotencyKey) {
        if (opinion == null || opinion.isBlank()) {
            throw new BusinessException(ErrorCode.BIZ_REQUEST_REJECT_OPINION_REQUIRED, "驳回必须填写审核意见");
        }
        return doTransition(id, version, ApprovalRecord.ACTION_REJECT,
                ApprovalRecord.RESULT_REJECTED, opinion, r -> {});
    }

    /**
     * 撤回: SUBMITTED → DRAFT。写审批记录(WITHDRAW)。
     */
    @Transactional
    public BizRequest withdraw(String id, String reason, Integer version, String idempotencyKey) {
        return doTransition(id, version, ApprovalRecord.ACTION_WITHDRAW, null, reason, r -> {});
    }

    /**
     * 归档: APPROVED → ARCHIVED。设置 archived_time，写审批记录(ARCHIVE)。
     */
    @Transactional
    public BizRequest archive(String id, Integer version, String idempotencyKey) {
        return doTransition(id, version, ApprovalRecord.ACTION_ARCHIVE, null, null,
                r -> r.setArchivedTime(OffsetDateTime.now()));
    }

    // ==================== 内部方法 ====================

    /**
     * 通用状态流转: 加载 → DataScope 校验 → 版本校验 → 幂等检查 → 状态机校验 → 更新 → 写审批记录。
     * 幂等: 若已处于目标状态，直接返回当前实体 (重复点击不报错)。
     * GA2-02: 在加载后立即校验 DataScope，防止越权审批/撤回/归档。
     */
    private BizRequest doTransition(String id, Integer version, String action,
                                    String result, String opinion,
                                    Consumer<BizRequest> fieldSetter) {
        BizRequest request = bizRequestMapper.selectById(id);
        if (request == null) {
            throw new ResourceNotFoundException(ErrorCode.BIZ_REQUEST_NOT_FOUND, "申请单不存在: " + id);
        }
        // GA2-02: DataScope 校验（防止越权审批/撤回/归档）
        checkDataScope(request);
        // 乐观锁版本检查
        checkVersion(version, request.getVersion());
        String targetStatus = domainService.nextStatus(action);
        // 幂等: 已处于目标状态则直接返回
        if (targetStatus.equals(request.getRequestStatus())) {
            return request;
        }
        // 状态机校验
        domainService.validateTransition(request.getRequestStatus(), action);
        String fromStatus = request.getRequestStatus();
        request.setRequestStatus(targetStatus);
        request.setUpdatedBy(CurrentUserContext.getUserId());
        fieldSetter.accept(request);
        int affected = bizRequestMapper.updateById(request);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        writeApprovalRecord(id, action, result, opinion);
        // GA2-32: 状态流转成功后发布事件，由 system-service 监听推送实时信封（44 号文档 line 130-139）
        // 事务提交后由 @TransactionalEventListener 处理，避免回滚时误推送
        eventPublisher.publishEvent(new BizRequestStateChangedEvent(
                request.getId(), request.getRequestNo(), fromStatus, targetStatus, action,
                request.getApplicantId(), request.getTenantId(), TraceContext.getTraceId()));
        return request;
    }

    /**
     * GA2-02: DataScope 校验。
     * 根据当前用户的 DataScope 检查是否可访问指定申请单。
     * 越权访问抛 DataScopeDeniedException (403 AUTH-403002)。
     *
     * 规则:
     *  - ALL             → 放行
     *  - TENANT          → 放行（tenant_id 已在 SQL 层过滤，此处不重复校验）
     *  - DEPT            → 校验 owner_dept_id 在 deptIds 集合内
     *  - DEPT_AND_CHILD  → 校验 owner_dept_path 以 deptPathPrefixes 任一前缀开头
     *  - SELF            → 校验 owner_user_id == 当前用户
     *  - CUSTOM          → 校验 id 在 resourceIds 白名单内；白名单空 → 拒绝
     *  - NONE            → 拒绝
     */
    private void checkDataScope(BizRequest request) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        if (scope == null) {
            // 兜底: 无 DataScope 信息 → 拒绝（安全默认）
            throw new DataScopeDeniedException("申请单[" + request.getId() + "]数据权限校验失败: 无 DataScope 信息");
        }
        switch (scope.scopeType()) {
            case ALL:
            case TENANT:
                // 放行
                return;
            case DEPT:
                if (scope.deptIds() == null || scope.deptIds().isEmpty()
                        || !scope.deptIds().contains(request.getOwnerDeptId())) {
                    throw new DataScopeDeniedException("申请单[" + request.getId() + "]不在本部门数据范围内");
                }
                return;
            case DEPT_AND_CHILD:
                if (scope.deptPathPrefixes() == null || scope.deptPathPrefixes().isEmpty()
                        || request.getOwnerDeptPath() == null) {
                    throw new DataScopeDeniedException("申请单[" + request.getId() + "]不在本部门及下级数据范围内");
                }
                boolean matched = false;
                for (String prefix : scope.deptPathPrefixes()) {
                    if (prefix != null && !prefix.isBlank()
                            && request.getOwnerDeptPath().startsWith(prefix)) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    throw new DataScopeDeniedException("申请单[" + request.getId() + "]不在本部门及下级数据范围内");
                }
                return;
            case SELF:
                if (request.getOwnerUserId() == null
                        || !request.getOwnerUserId().equals(scope.userId())) {
                    throw new DataScopeDeniedException("申请单[" + request.getId() + "]不是您创建的数据");
                }
                return;
            case CUSTOM:
                // 67 号文档第 52 行硬约束: 白名单为空 → 拒绝，严禁降级为 ALL
                if (scope.resourceIds() == null || scope.resourceIds().isEmpty()) {
                    throw new DataScopeDeniedException("申请单[" + request.getId() + "]不在自定义数据范围白名单内");
                }
                if (!scope.resourceIds().contains(request.getId())) {
                    throw new DataScopeDeniedException("申请单[" + request.getId() + "]不在自定义数据范围白名单内");
                }
                return;
            case NONE:
                throw new DataScopeDeniedException("申请单[" + request.getId() + "]数据范围 NONE，禁止访问");
        }
    }

    /**
     * 写审批记录。operated_time = OffsetDateTime.now()。
     */
    private void writeApprovalRecord(String requestId, String action, String result, String opinion) {
        ApprovalRecord record = new ApprovalRecord();
        record.setId(IdGenerator.nextId());
        record.setTenantId(CurrentUserContext.getTenantId());
        record.setCreatedBy(CurrentUserContext.getUserId());
        record.setRequestId(requestId);
        record.setAction(action);
        record.setResult(result);
        record.setOpinion(opinion);
        record.setOperatorId(CurrentUserContext.getUserId());
        record.setOperatedTime(OffsetDateTime.now());
        approvalRecordMapper.insert(record);
    }

    /**
     * 明细保存: 全删全插 (先逻辑删除旧明细，再插入新明细)。
     * sort_no 由前端提供，缺失时按序号校正。
     *
     * GA2-L191: product_code_snapshot / product_name_snapshot / unit 由服务端根据 productId 批量反查主数据填充，
     *  避免前端透传不可信快照；批量查询 (一次 IN) 避免 N+1。
     */
    private void saveItems(String requestId, List<SaveBizRequestRequest.Item> items) {
        // 逻辑删除旧明细
        bizRequestItemMapper.delete(new LambdaQueryWrapper<BizRequestItem>()
                .eq(BizRequestItem::getRequestId, requestId));
        if (items == null || items.isEmpty()) {
            return;
        }
        // 批量查询所有 productId 对应的商品主数据 (一次 IN，避免 N+1)
        List<String> productIds = items.stream()
                .map(SaveBizRequestRequest.Item::getProductId)
                .filter(pid -> pid != null && !pid.isBlank())
                .distinct()
                .collect(Collectors.toList());
        Map<String, Product> productMap = new HashMap<>();
        if (!productIds.isEmpty()) {
            List<Product> products = productMapper.selectList(new LambdaQueryWrapper<Product>()
                    .in(Product::getId, productIds));
            for (Product p : products) {
                productMap.put(p.getId(), p);
            }
        }
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        int index = 1;
        for (SaveBizRequestRequest.Item item : items) {
            BizRequestItem entity = new BizRequestItem();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCreatedBy(userId);
            entity.setRequestId(requestId);
            entity.setProductId(item.getProductId());
            // 优先使用服务端反查的快照，前端传入值仅作兜底 (productMap 未命中时保留前端值，兼容历史数据)
            Product product = productMap.get(item.getProductId());
            if (product != null) {
                entity.setProductCodeSnapshot(product.getProductCode());
                entity.setProductNameSnapshot(product.getProductName());
                if (item.getUnit() == null || item.getUnit().isBlank()) {
                    entity.setUnit(product.getUnit());
                } else {
                    entity.setUnit(item.getUnit());
                }
            } else {
                entity.setProductCodeSnapshot(item.getProductCodeSnapshot());
                entity.setProductNameSnapshot(item.getProductNameSnapshot());
                entity.setUnit(item.getUnit());
            }
            entity.setQuantity(item.getQuantity());
            entity.setUnitPrice(item.getUnitPrice());
            entity.setLineAmount(calculateLineAmount(item.getQuantity(), item.getUnitPrice()));
            entity.setSortNo(item.getSortNo() != null ? item.getSortNo() : index);
            bizRequestItemMapper.insert(entity);
            index++;
        }
    }

    /**
     * 根据 customerId 反查客户主数据填充 customer_name_snapshot。
     * GA2-L191: 快照字段必须由服务端反查主数据填充，前端传入值仅作兜底 (兼容历史/外部数据)。
     *  customer 不存在时保留前端传入值，避免阻塞业务流程 (允许后续人工修正)。
     */
    private String resolveCustomerName(String customerId, String fallback) {
        if (customerId == null || customerId.isBlank()) {
            return fallback;
        }
        Customer customer = customerMapper.selectById(customerId);
        if (customer != null && customer.getCustomerName() != null && !customer.getCustomerName().isBlank()) {
            return customer.getCustomerName();
        }
        return fallback;
    }

    /**
     * line_amount = quantity * unit_price，保留 2 位小数。
     */
    private BigDecimal calculateLineAmount(BigDecimal quantity, BigDecimal unitPrice) {
        BigDecimal qty = quantity != null ? quantity : BigDecimal.ZERO;
        BigDecimal price = unitPrice != null ? unitPrice : BigDecimal.ZERO;
        return qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * total_amount = sum(line_amount)，保留 2 位小数。
     */
    private BigDecimal calculateTotalAmount(List<SaveBizRequestRequest.Item> items) {
        if (items == null || items.isEmpty()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return items.stream()
                .map(item -> calculateLineAmount(item.getQuantity(), item.getUnitPrice()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 乐观锁版本检查，version 不匹配抛 SYS_OPTIMISTIC_LOCK (SYS-409001)。
     * GA2-L189: 对齐 openapi.yaml versionedUpdate/businessDraftUpdate profile 乐观锁错误码。
     */
    private void checkVersion(Integer expected, Integer actual) {
        if (expected != null && !expected.equals(actual)) {
            throw new BusinessException(ErrorCode.SYS_OPTIMISTIC_LOCK, "数据版本已变化，请刷新后重试");
        }
    }
}
