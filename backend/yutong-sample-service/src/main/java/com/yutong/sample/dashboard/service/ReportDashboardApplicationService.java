package com.yutong.sample.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.yutong.sample.dashboard.domain.RptReport;
import com.yutong.sample.dashboard.dto.ReportPageQuery;
import com.yutong.sample.dashboard.dto.SaveReportRequest;
import com.yutong.sample.dashboard.mapper.RptDashboardReportMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 报表设计器应用服务。设计来源: 42-报表与大屏可视化设计 R2。
 * <p>
 * 提供报表的 CRUD、发布、渲染等能力，支持拖拽式报表设计器。
 */
@Service
public class ReportDashboardApplicationService {

    /** 报表资源编码，对齐 permissions.yaml report:* 命名。 */
    public static final String RESOURCE_CODE = "report";

    private final RptDashboardReportMapper reportMapper;
    private final DataScopeResolver dataScopeResolver;

    public ReportDashboardApplicationService(RptDashboardReportMapper reportMapper,
                                              DataScopeResolver dataScopeResolver) {
        this.reportMapper = reportMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 分页查询报表列表
     */
    public PageResult<RptReport> page(PageRequest request, ReportPageQuery query) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<RptReport> qw = new LambdaQueryWrapper<RptReport>()
                .eq(RptReport::getTenantId, CurrentUserContext.getTenantId())
                .like(query.keyword() != null && !query.keyword().isBlank(), RptReport::getReportName, query.keyword())
                .eq(query.status() != null && !query.status().isBlank(), RptReport::getStatus, query.status())
                .eq(query.reportType() != null && !query.reportType().isBlank(), RptReport::getReportType, query.reportType())
                .orderByDesc(RptReport::getCreatedTime);
        applyDataScope(qw, scope);
        Page<RptReport> page = reportMapper.selectPage(new Page<>(request.page(), request.size()), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * RptReport 实体有 owner_user_id 字段，使用 owner_user_id 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF/DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 安全降级为 owner_user_id = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<RptReport> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(RptReport::getOwnerUserId, userId);
    }

    /**
     * 查询报表详情
     */
    public RptReport get(String reportCode) {
        return findReportByCode(reportCode);
    }

    /**
     * 创建报表
     */
    @Transactional
    public RptReport create(SaveReportRequest request) {
        Long count = reportMapper.selectCount(new LambdaQueryWrapper<RptReport>()
                .eq(RptReport::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptReport::getReportCode, request.reportCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "报表编码已存在: " + request.reportCode());
        }
        RptReport report = new RptReport();
        report.setId(IdGenerator.nextId());
        report.setReportCode(request.reportCode());
        report.setReportName(request.reportName());
        report.setReportType(request.reportType() != null ? request.reportType() : RptReport.TYPE_CHART);
        report.setLayoutJson(request.layoutJson());
        report.setDatasetBindings(request.datasetBindings());
        report.setVersionNo(1);
        report.setPermissionCode(request.permissionCode());
        report.setStatus(RptReport.STATUS_DRAFT);
        report.setOwnerUserId(CurrentUserContext.getUserId());
        report.setDescription(request.description());
        reportMapper.insert(report);
        return report;
    }

    /**
     * 更新报表
     */
    @Transactional
    public RptReport update(String reportCode, SaveReportRequest request) {
        RptReport report = findReportByCode(reportCode);
        if (request.reportName() != null) report.setReportName(request.reportName());
        if (request.reportType() != null) report.setReportType(request.reportType());
        if (request.layoutJson() != null) {
            report.setLayoutJson(request.layoutJson());
            report.setVersionNo(report.getVersionNo() + 1);
        }
        if (request.datasetBindings() != null) report.setDatasetBindings(request.datasetBindings());
        if (request.permissionCode() != null) report.setPermissionCode(request.permissionCode());
        if (request.description() != null) report.setDescription(request.description());
        reportMapper.updateById(report);
        return report;
    }

    /**
     * 删除报表
     */
    @Transactional
    public void delete(String reportCode) {
        RptReport report = findReportByCode(reportCode);
        reportMapper.deleteById(report.getId());
    }

    /**
     * 发布报表
     */
    @Transactional
    public RptReport publish(String reportCode) {
        RptReport report = findReportByCode(reportCode);
        report.setStatus(RptReport.STATUS_PUBLISHED);
        reportMapper.updateById(report);
        return report;
    }

    /**
     * 渲染报表（返回 layoutJson 供前端渲染）
     */
    public RptReport render(String reportCode) {
        RptReport report = findReportByCode(reportCode);
        if (!RptReport.STATUS_PUBLISHED.equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.RPT_PERMISSION_DENIED,
                    "报表未发布，不可渲染: " + reportCode);
        }
        return report;
    }

    private RptReport findReportByCode(String reportCode) {
        RptReport report = reportMapper.selectOne(new LambdaQueryWrapper<RptReport>()
                .eq(RptReport::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptReport::getReportCode, reportCode)
                .eq(RptReport::getDeleted, false));
        if (report == null) {
            throw new ResourceNotFoundException(ErrorCode.RPT_NOT_FOUND, "报表不存在: " + reportCode);
        }
        return report;
    }
}
