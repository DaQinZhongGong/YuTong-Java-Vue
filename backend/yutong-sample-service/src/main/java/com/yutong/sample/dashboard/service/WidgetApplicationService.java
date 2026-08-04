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
import com.yutong.sample.dashboard.domain.RptWidget;
import com.yutong.sample.dashboard.dto.SaveWidgetRequest;
import com.yutong.sample.dashboard.mapper.RptWidgetMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Widget 应用服务。设计来源: 42-报表与大屏可视化设计。
 * <p>
 * 提供 Widget 组件的 CRUD 能力，用于报表设计器和大屏设计器。
 */
@Service
public class WidgetApplicationService {

    /** Widget 资源编码，对齐 permissions.yaml widget:* 命名。 */
    public static final String RESOURCE_CODE = "widget";

    private final RptWidgetMapper widgetMapper;
    private final DataScopeResolver dataScopeResolver;

    public WidgetApplicationService(RptWidgetMapper widgetMapper,
                                     DataScopeResolver dataScopeResolver) {
        this.widgetMapper = widgetMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 分页查询 Widget 列表
     */
    public PageResult<RptWidget> page(PageRequest request, String keyword, String widgetType) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<RptWidget> qw = new LambdaQueryWrapper<RptWidget>()
                .eq(RptWidget::getTenantId, CurrentUserContext.getTenantId())
                .like(keyword != null && !keyword.isBlank(), RptWidget::getWidgetName, keyword)
                .eq(widgetType != null && !widgetType.isBlank(), RptWidget::getWidgetType, widgetType)
                .orderByDesc(RptWidget::getCreatedTime);
        applyDataScope(qw, scope);
        Page<RptWidget> page = widgetMapper.selectPage(new Page<>(request.page(), request.size()), qw);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * RptWidget 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF/DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<RptWidget> wrapper, DataScope scope) {
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
        wrapper.eq(RptWidget::getCreatedBy, userId);
    }

    /**
     * 查询 Widget 详情
     */
    public RptWidget get(String widgetCode) {
        return findWidgetByCode(widgetCode);
    }

    /**
     * 创建 Widget
     */
    @Transactional
    public RptWidget create(SaveWidgetRequest request) {
        Long count = widgetMapper.selectCount(new LambdaQueryWrapper<RptWidget>()
                .eq(RptWidget::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptWidget::getWidgetCode, request.widgetCode()));
        if (count > 0) {
            throw new BusinessException(ErrorCode.SYS_BUSINESS_CONFLICT,
                    "Widget 编码已存在: " + request.widgetCode());
        }
        RptWidget widget = new RptWidget();
        widget.setId(IdGenerator.nextId());
        widget.setWidgetCode(request.widgetCode());
        widget.setWidgetName(request.widgetName());
        widget.setWidgetType(request.widgetType());
        widget.setDatasetCode(request.datasetCode());
        widget.setPropsJson(request.propsJson());
        widget.setStyleJson(request.styleJson());
        widget.setDescription(request.description());
        widgetMapper.insert(widget);
        return widget;
    }

    /**
     * 更新 Widget
     */
    @Transactional
    public RptWidget update(String widgetCode, SaveWidgetRequest request) {
        RptWidget widget = findWidgetByCode(widgetCode);
        if (request.widgetName() != null) widget.setWidgetName(request.widgetName());
        if (request.widgetType() != null) widget.setWidgetType(request.widgetType());
        if (request.datasetCode() != null) widget.setDatasetCode(request.datasetCode());
        if (request.propsJson() != null) widget.setPropsJson(request.propsJson());
        if (request.styleJson() != null) widget.setStyleJson(request.styleJson());
        if (request.description() != null) widget.setDescription(request.description());
        widgetMapper.updateById(widget);
        return widget;
    }

    /**
     * 删除 Widget
     */
    @Transactional
    public void delete(String widgetCode) {
        RptWidget widget = findWidgetByCode(widgetCode);
        widgetMapper.deleteById(widget.getId());
    }

    private RptWidget findWidgetByCode(String widgetCode) {
        RptWidget widget = widgetMapper.selectOne(new LambdaQueryWrapper<RptWidget>()
                .eq(RptWidget::getTenantId, CurrentUserContext.getTenantId())
                .eq(RptWidget::getWidgetCode, widgetCode)
                .eq(RptWidget::getDeleted, false));
        if (widget == null) {
            throw new ResourceNotFoundException(ErrorCode.RPT_NOT_FOUND, "Widget 不存在: " + widgetCode);
        }
        return widget;
    }
}
