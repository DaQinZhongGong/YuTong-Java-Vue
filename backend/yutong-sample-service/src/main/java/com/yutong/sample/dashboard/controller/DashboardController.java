package com.yutong.sample.dashboard.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.dashboard.domain.RptDashboard;
import com.yutong.sample.dashboard.dto.DashboardPageQuery;
import com.yutong.sample.dashboard.dto.SaveDashboardRequest;
import com.yutong.sample.dashboard.service.DashboardApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 大屏 Controller。设计来源: 42-报表与大屏可视化设计 R3。
 * <p>
 * 提供大屏的 CRUD、发布、全屏渲染等 API 端点。
 * 权限码: dashboard:view/create/edit/delete/publish
 */
@Tag(name = "大屏设计器")
@RestController
@RequestMapping("/api/v1/dashboards")
public class DashboardController {

    private final DashboardApplicationService dashboardService;

    public DashboardController(DashboardApplicationService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "分页查询大屏列表", operationId = "pageDashboards")
    @RequiresPermission("dashboard:view")
    @GetMapping
    public Result<PageResult<RptDashboard>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String theme) {
        PageRequest request = PageRequest.of(page, size);
        DashboardPageQuery query = new DashboardPageQuery(keyword, status, theme);
        return Result.ok(dashboardService.page(request, query), TraceContext.getTraceId());
    }

    @Operation(summary = "查询大屏详情", operationId = "getDashboard")
    @RequiresPermission("dashboard:view")
    @GetMapping("/{code}")
    public Result<RptDashboard> get(@PathVariable String code) {
        return Result.ok(dashboardService.get(code), TraceContext.getTraceId());
    }

    @Operation(summary = "创建大屏", operationId = "createDashboard")
    @RequiresPermission("dashboard:create")
    @Auditable(operationType = "CREATE", module = "dashboard", bizType = "rpt_dashboard",
            bizIdExpr = "#result.data.dashboardCode", content = "创建大屏", recordResult = true)
    @PostMapping
    public Result<RptDashboard> create(@Valid @RequestBody SaveDashboardRequest request) {
        return Result.ok(dashboardService.create(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新大屏", operationId = "updateDashboard")
    @RequiresPermission("dashboard:edit")
    @Auditable(operationType = "UPDATE", module = "dashboard", bizType = "rpt_dashboard",
            bizIdExpr = "#code", content = "更新大屏")
    @PutMapping("/{code}")
    public Result<RptDashboard> update(@PathVariable String code,
                                        @Valid @RequestBody SaveDashboardRequest request) {
        return Result.ok(dashboardService.update(code, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除大屏", operationId = "deleteDashboard")
    @RequiresPermission("dashboard:delete")
    @Auditable(operationType = "DELETE", module = "dashboard", bizType = "rpt_dashboard",
            bizIdExpr = "#code", content = "删除大屏")
    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        dashboardService.delete(code);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "发布大屏", operationId = "publishDashboard")
    @RequiresPermission("dashboard:publish")
    @Auditable(operationType = "PUBLISH", module = "dashboard", bizType = "rpt_dashboard",
            bizIdExpr = "#code", content = "发布大屏")
    @PostMapping("/{code}/publish")
    public Result<RptDashboard> publish(@PathVariable String code) {
        return Result.ok(dashboardService.publish(code), TraceContext.getTraceId());
    }

    @Operation(summary = "全屏渲染大屏", operationId = "renderFullscreen")
    @RequiresPermission("dashboard:view")
    @GetMapping("/{code}/fullscreen")
    public Result<RptDashboard> renderFullscreen(@PathVariable String code) {
        return Result.ok(dashboardService.renderFullscreen(code), TraceContext.getTraceId());
    }
}
