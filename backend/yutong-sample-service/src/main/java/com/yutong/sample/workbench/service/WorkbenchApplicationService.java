package com.yutong.sample.workbench.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.sample.masterdata.domain.Customer;
import com.yutong.sample.masterdata.mapper.CustomerMapper;
import com.yutong.sample.masterdata.mapper.ProductMapper;
import com.yutong.sample.request.domain.BizRequest;
import com.yutong.sample.request.mapper.BizRequestMapper;
import com.yutong.sample.workbench.dto.WorkbenchStatsVO;
import com.yutong.sample.workbench.dto.WorkbenchTopItemVO;
import com.yutong.sample.workbench.dto.WorkbenchTrendVO;
import com.yutong.system.message.domain.SysMessage;
import com.yutong.system.message.domain.SysTodoTask;
import com.yutong.system.message.mapper.SysMessageMapper;
import com.yutong.system.message.mapper.SysTodoTaskMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台应用服务。设计来源: 18-样例业务详细设计 工作台统计、42-报表与大屏可视化设计 R0 验收标准。
 * <p>
 * 聚合客户、商品、申请单各状态、待办、消息的统计数据。
 * <p>
 * GA2-33: 新增近 N 日提交趋势（对齐 42 号文档 biz_request_trend_7d 数据集）
 *         和金额 Top N（对齐 biz_request_amount_top10 数据集），支撑前端 ECharts 图表。
 */
@Service
public class WorkbenchApplicationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 工作台资源编码，对齐 permissions.yaml workbench:* 命名。 */
    public static final String RESOURCE_CODE = "workbench";

    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    private final BizRequestMapper requestMapper;
    private final SysTodoTaskMapper todoTaskMapper;
    private final SysMessageMapper messageMapper;
    private final DataScopeResolver dataScopeResolver;

    public WorkbenchApplicationService(CustomerMapper customerMapper,
                                       ProductMapper productMapper,
                                       BizRequestMapper requestMapper,
                                       SysTodoTaskMapper todoTaskMapper,
                                       SysMessageMapper messageMapper,
                                       DataScopeResolver dataScopeResolver) {
        this.customerMapper = customerMapper;
        this.productMapper = productMapper;
        this.requestMapper = requestMapper;
        this.todoTaskMapper = todoTaskMapper;
        this.messageMapper = messageMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * GA2-DS: 解析当前用户对 workbench 资源的数据权限。
     * 工作台为聚合统计仪表盘，租户内所有用户可见租户级聚合数据 (admin 放行 = 无 owner 过滤)。
     * NONE 范围 → 调用方返回空结果。
     */
    private DataScope resolveScope() {
        return dataScopeResolver.resolve(RESOURCE_CODE);
    }

    /**
     * 获取工作台统计数据。
     */
    public WorkbenchStatsVO getStats() {
        DataScope scope = resolveScope();
        if (scope != null && scope.scopeType() == DataScopeType.NONE) {
            return WorkbenchStatsVO.builder().build();
        }
        String tenantId = CurrentUserContext.getTenantId();
        long totalCustomers = customerMapper.selectCount(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getTenantId, tenantId));
        long totalProducts = productMapper.selectCount(new LambdaQueryWrapper<com.yutong.sample.masterdata.domain.Product>()
                .eq(com.yutong.sample.masterdata.domain.Product::getTenantId, tenantId));
        long totalRequests = requestMapper.selectCount(new LambdaQueryWrapper<BizRequest>()
                .eq(BizRequest::getTenantId, tenantId));

        long draft = requestMapper.selectCount(new LambdaQueryWrapper<BizRequest>()
                .eq(BizRequest::getTenantId, tenantId)
                .eq(BizRequest::getRequestStatus, BizRequest.STATUS_DRAFT));
        long submitted = requestMapper.selectCount(new LambdaQueryWrapper<BizRequest>()
                .eq(BizRequest::getTenantId, tenantId)
                .eq(BizRequest::getRequestStatus, BizRequest.STATUS_SUBMITTED));
        long approved = requestMapper.selectCount(new LambdaQueryWrapper<BizRequest>()
                .eq(BizRequest::getTenantId, tenantId)
                .eq(BizRequest::getRequestStatus, BizRequest.STATUS_APPROVED));
        long rejected = requestMapper.selectCount(new LambdaQueryWrapper<BizRequest>()
                .eq(BizRequest::getTenantId, tenantId)
                .eq(BizRequest::getRequestStatus, BizRequest.STATUS_REJECTED));
        long archived = requestMapper.selectCount(new LambdaQueryWrapper<BizRequest>()
                .eq(BizRequest::getTenantId, tenantId)
                .eq(BizRequest::getRequestStatus, BizRequest.STATUS_ARCHIVED));

        long pendingTodos = todoTaskMapper.selectCount(new LambdaQueryWrapper<SysTodoTask>()
                .eq(SysTodoTask::getTenantId, tenantId)
                .eq(SysTodoTask::getTodoStatus, "PENDING"));
        long unreadMessages = messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, tenantId)
                .eq(SysMessage::getReadStatus, "UNREAD"));

        return WorkbenchStatsVO.builder()
                .totalCustomers(totalCustomers)
                .totalProducts(totalProducts)
                .totalRequests(totalRequests)
                .draftRequests(draft)
                .submittedRequests(submitted)
                .approvedRequests(approved)
                .rejectedRequests(rejected)
                .archivedRequests(archived)
                .pendingTodos(pendingTodos)
                .unreadMessages(unreadMessages)
                .build();
    }

    /**
     * GA2-33: 获取近 N 日申请单提交趋势。对齐 42 号文档 biz_request_trend_7d 数据集。
     * <p>
     * 按日聚合 created_time，统计每日新增申请单数和金额合计。
     * 没有数据的日期补零，保证前端折线图 x 轴连续。
     *
     * @param days 天数，默认 7，上限 90
     * @return 趋势数据（points 按日期升序，含补零日期）
     */
    public WorkbenchTrendVO getRecentTrend(int days) {
        DataScope scope = resolveScope();
        if (scope != null && scope.scopeType() == DataScopeType.NONE) {
            return WorkbenchTrendVO.builder().points(List.of()).totalCount(0L).totalAmount(BigDecimal.ZERO).build();
        }
        int safeDays = Math.max(1, Math.min(days, 90));
        LocalDate today = LocalDate.now(ZONE);
        LocalDate startDate = today.minusDays(safeDays - 1L);
        String tenantId = CurrentUserContext.getTenantId();

        // 查询时间范围内的申请单（只取需要的字段，减少传输）
        OffsetDateTime startDateTime = startDate.atStartOfDay(ZONE).toOffsetDateTime();
        List<BizRequest> recent = requestMapper.selectList(new LambdaQueryWrapper<BizRequest>()
                .eq(BizRequest::getTenantId, tenantId)
                .ge(BizRequest::getCreatedTime, startDateTime)
                .select(BizRequest::getCreatedTime, BizRequest::getTotalAmount));

        // 按日期分组聚合
        Map<String, long[]> dayMap = new LinkedHashMap<>();
        for (BizRequest req : recent) {
            if (req.getCreatedTime() == null) continue;
            String day = req.getCreatedTime().atZoneSameInstant(ZONE).toLocalDate().format(DATE_FMT);
            long[] agg = dayMap.computeIfAbsent(day, k -> new long[]{0, 0});
            agg[0]++; // count
            BigDecimal amt = req.getTotalAmount();
            if (amt != null) {
                agg[1] += amt.movePointRight(2).longValue(); // 用分存储避免浮点误差
            }
        }

        // 补齐无数据日期 + 组装结果
        List<WorkbenchTrendVO.TrendPoint> points = new ArrayList<>(safeDays);
        long totalCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (int i = 0; i < safeDays; i++) {
            LocalDate day = startDate.plusDays(i);
            String dayStr = day.format(DATE_FMT);
            long[] agg = dayMap.get(dayStr);
            long count = agg == null ? 0 : agg[0];
            BigDecimal amount = agg == null ? BigDecimal.ZERO : BigDecimal.valueOf(agg[1], 2);
            points.add(WorkbenchTrendVO.TrendPoint.builder()
                    .date(dayStr)
                    .count(count)
                    .amount(amount)
                    .build());
            totalCount += count;
            totalAmount = totalAmount.add(amount);
        }

        return WorkbenchTrendVO.builder()
                .points(points)
                .totalCount(totalCount)
                .totalAmount(totalAmount)
                .build();
    }

    /**
     * GA2-33: 获取金额 Top N 申请单。对齐 42 号文档 biz_request_amount_top10 数据集。
     * <p>
     * 排除 DRAFT 草稿（未提交的单据金额意义不大），按 total_amount 降序取前 N。
     *
     * @param limit 条数，默认 10，上限 50
     * @return Top N 列表
     */
    public List<WorkbenchTopItemVO> getAmountTop(int limit) {
        DataScope scope = resolveScope();
        if (scope != null && scope.scopeType() == DataScopeType.NONE) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 50));
        String tenantId = CurrentUserContext.getTenantId();
        List<BizRequest> top = requestMapper.selectList(new LambdaQueryWrapper<BizRequest>()
                .eq(BizRequest::getTenantId, tenantId)
                .ne(BizRequest::getRequestStatus, BizRequest.STATUS_DRAFT)
                .isNotNull(BizRequest::getTotalAmount)
                .orderByDesc(BizRequest::getTotalAmount)
                .last("LIMIT " + safeLimit));
        return top.stream().map(req -> WorkbenchTopItemVO.builder()
                .requestNo(req.getRequestNo())
                .title(req.getTitle())
                .totalAmount(req.getTotalAmount())
                .customerNameSnapshot(req.getCustomerNameSnapshot())
                .requestStatus(req.getRequestStatus())
                .build()).toList();
    }
}
