package com.yutong.sample.dashboard.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.dashboard.domain.RptReport;
import com.yutong.sample.dashboard.dto.ReportPageQuery;
import com.yutong.sample.dashboard.dto.SaveReportRequest;
import com.yutong.sample.dashboard.service.ReportDashboardApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 报表设计器 Controller。设计来源: 42-报表与大屏可视化设计 R2。
 * <p>
 * 提供报表的 CRUD、发布、渲染等 API 端点。
 * 权限码: report:view/create/edit/delete/publish
 */
@Tag(name = "报表设计器")
@RestController
@RequestMapping("/api/v1/dashboard/reports")
public class ReportDashboardController {

    private final ReportDashboardApplicationService reportService;

    public ReportDashboardController(ReportDashboardApplicationService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "分页查询报表列表", operationId = "pageReports")
    @RequiresPermission("report:view")
    @GetMapping
    public Result<PageResult<RptReport>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String reportType) {
        PageRequest request = PageRequest.of(page, size);
        ReportPageQuery query = new ReportPageQuery(keyword, status, reportType);
        return Result.ok(reportService.page(request, query), TraceContext.getTraceId());
    }

    @Operation(summary = "查询报表详情", operationId = "getReport")
    @RequiresPermission("report:view")
    @GetMapping("/{code}")
    public Result<RptReport> get(@PathVariable String code) {
        return Result.ok(reportService.get(code), TraceContext.getTraceId());
    }

    @Operation(summary = "创建报表", operationId = "createReport")
    @RequiresPermission("report:create")
    @Auditable(operationType = "CREATE", module = "dashboard", bizType = "rpt_report",
            bizIdExpr = "#result.data.reportCode", content = "创建报表", recordResult = true)
    @PostMapping
    public Result<RptReport> create(@Valid @RequestBody SaveReportRequest request) {
        return Result.ok(reportService.create(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新报表", operationId = "updateReport")
    @RequiresPermission("report:edit")
    @Auditable(operationType = "UPDATE", module = "dashboard", bizType = "rpt_report",
            bizIdExpr = "#code", content = "更新报表")
    @PutMapping("/{code}")
    public Result<RptReport> update(@PathVariable String code,
                                     @Valid @RequestBody SaveReportRequest request) {
        return Result.ok(reportService.update(code, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除报表", operationId = "deleteReport")
    @RequiresPermission("report:delete")
    @Auditable(operationType = "DELETE", module = "dashboard", bizType = "rpt_report",
            bizIdExpr = "#code", content = "删除报表")
    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        reportService.delete(code);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "发布报表", operationId = "publishReport")
    @RequiresPermission("report:publish")
    @Auditable(operationType = "PUBLISH", module = "dashboard", bizType = "rpt_report",
            bizIdExpr = "#code", content = "发布报表")
    @PostMapping("/{code}/publish")
    public Result<RptReport> publish(@PathVariable String code) {
        return Result.ok(reportService.publish(code), TraceContext.getTraceId());
    }

    @Operation(summary = "渲染报表", operationId = "renderReport")
    @RequiresPermission("report:view")
    @GetMapping("/{code}/render")
    public Result<RptReport> render(@PathVariable String code) {
        return Result.ok(reportService.render(code), TraceContext.getTraceId());
    }
}
