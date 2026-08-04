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
import com.yutong.sample.dashboard.domain.RptDashboard;
import com.yutong.sample.dashboard.dto.DashboardPageQuery;
import com.yutong.sample.dashboard.dto.SaveDashboardRequest;
import com.yutong.sample.dashboard.mapper.RptDashboardMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 大屏应用服务。设计来源: 42-报表与大屏可视化设计 R3。
 * <p>
 * 提供大屏的 CRUD、发布、全屏渲染等能力，支持拖拽式大屏设计器。
 */
@Service
public class DashboardApplicationService {

    /** 大屏资源编码，对齐 permissions.yaml dashboard:* 命名。 */
    public static final String RESOURCE_CODE = "dashboard";

    private final RptDashboardMapper dashboardMapper;
    private final DataScopeResolver dataScopeResolver;

    public DashboardApplicationService(RptDashboardMapper dashboardMapper,
                                        DataScopeResolver dataScopeResolver) {
        this.dashboardMapper = dashboardMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 分页查询大屏列表
     */
    public PageResult<RptDashboard> page(PageRequest request, DashboardPageQuery query) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<RptDashboard> qw = new LambdaQueryWrapper<RptDashboard>()
                .eq(RptDashboard::getTenantId, CurrentUserContext.getTenantId())
                .like(query.keyword() != null && !query.keyword().isBlank(), RptDashboard::getDashboardName, query.keyword())
                .eq(query.status() != null && !query.status().isBlank(), RptDashboard::getStatus, query.status())
                .eq(query.theme() != null && !query.theme().isBlank(), RptDashboard::getTheme, query.theme())
                .orderByDesc(RptDashboard::getCreatedTime);
        applyDataScope(qw, scope);
        Page<RptDashboard> page = dashboardMapper.selectPage(new Page<>(request.page(), request.size()), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * RptDashboard 实体有 owner_user_id 字段，使用 owner_user_id 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF/DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 安全降级为 owner_user_id = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<RptDashboard> wrapper, DataScope scope) {
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
        wrapper.eq(RptDashboard::getOwnerUserId, userId);
    }

    /**
     * 查询大屏详情
     */
    public RptDashboard get(String dashboardCode) {
        return findDashboardByCode(dashboardCode);
    }

    /**
     * 创建大屏
     */
    @Transactional
    public RptDashboard create(SaveDashboardRequest request) {
        Long count = dashboardMapper.selectCount(new LambdaQueryWrapper<RptDashboard>()
                .eq(RptDashboard::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptDashboard::getDashboardCode, request.dashboardCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "大屏编码已存在: " + request.dashboardCode());
        }
        RptDashboard dashboard = new RptDashboard();
        dashboard.setId(IdGenerator.nextId());
        dashboard.setDashboardCode(request.dashboardCode());
        dashboard.setDashboardName(request.dashboardName());
        dashboard.setCanvasWidth(request.canvasWidth() != null ? request.canvasWidth() : 1920);
        dashboard.setCanvasHeight(request.canvasHeight() != null ? request.canvasHeight() : 1080);
        dashboard.setTheme(request.theme() != null ? request.theme() : RptDashboard.THEME_DARK);
        dashboard.setBackgroundImage(request.backgroundImage());
        dashboard.setLayoutJson(request.layoutJson());
        dashboard.setComponentBindings(request.componentBindings());
        dashboard.setRefreshInterval(request.refreshInterval() != null ? request.refreshInterval() : 30);
        dashboard.setStatus(RptDashboard.STATUS_DRAFT);
        dashboard.setOwnerUserId(CurrentUserContext.getUserId());
        dashboard.setPermissionCode(request.permissionCode());
        dashboard.setDescription(request.description());
        dashboardMapper.insert(dashboard);
        return dashboard;
    }

    /**
     * 更新大屏
     */
    @Transactional
    public RptDashboard update(String dashboardCode, SaveDashboardRequest request) {
        RptDashboard dashboard = findDashboardByCode(dashboardCode);
        if (request.dashboardName() != null) dashboard.setDashboardName(request.dashboardName());
        if (request.canvasWidth() != null) dashboard.setCanvasWidth(request.canvasWidth());
        if (request.canvasHeight() != null) dashboard.setCanvasHeight(request.canvasHeight());
        if (request.theme() != null) dashboard.setTheme(request.theme());
        if (request.backgroundImage() != null) dashboard.setBackgroundImage(request.backgroundImage());
        if (request.layoutJson() != null) dashboard.setLayoutJson(request.layoutJson());
        if (request.componentBindings() != null) dashboard.setComponentBindings(request.componentBindings());
        if (request.refreshInterval() != null) dashboard.setRefreshInterval(request.refreshInterval());
        if (request.permissionCode() != null) dashboard.setPermissionCode(request.permissionCode());
        if (request.description() != null) dashboard.setDescription(request.description());
        dashboardMapper.updateById(dashboard);
        return dashboard;
    }

    /**
     * 删除大屏
     */
    @Transactional
    public void delete(String dashboardCode) {
        RptDashboard dashboard = findDashboardByCode(dashboardCode);
        dashboardMapper.deleteById(dashboard.getId());
    }

    /**
     * 发布大屏
     */
    @Transactional
    public RptDashboard publish(String dashboardCode) {
        RptDashboard dashboard = findDashboardByCode(dashboardCode);
        dashboard.setStatus(RptDashboard.STATUS_PUBLISHED);
        dashboardMapper.updateById(dashboard);
        return dashboard;
    }

    /**
     * 全屏渲染大屏（返回 layoutJson 供前端全屏渲染）
     */
    public RptDashboard renderFullscreen(String dashboardCode) {
        RptDashboard dashboard = findDashboardByCode(dashboardCode);
        if (!RptDashboard.STATUS_PUBLISHED.equals(dashboard.getStatus())) {
            throw new BusinessException(ErrorCode.RPT_PERMISSION_DENIED,
                    "大屏未发布，不可全屏渲染: " + dashboardCode);
        }
        return dashboard;
    }

    private RptDashboard findDashboardByCode(String dashboardCode) {
        RptDashboard dashboard = dashboardMapper.selectOne(new LambdaQueryWrapper<RptDashboard>()
                .eq(RptDashboard::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptDashboard::getDashboardCode, dashboardCode)
                .eq(RptDashboard::getDeleted, false));
        if (dashboard == null) {
            throw new ResourceNotFoundException(ErrorCode.RPT_NOT_FOUND, "大屏不存在: " + dashboardCode);
        }
        return dashboard;
    }
}
