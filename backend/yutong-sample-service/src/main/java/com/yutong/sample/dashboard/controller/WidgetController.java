package com.yutong.sample.dashboard.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.dashboard.domain.RptWidget;
import com.yutong.sample.dashboard.dto.SaveWidgetRequest;
import com.yutong.sample.dashboard.service.WidgetApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * Widget Controller。设计来源: 42-报表与大屏可视化设计。
 * <p>
 * 提供 Widget 组件的 CRUD API 端点。
 * 权限码: widget:view/create/edit/delete
 */
@Tag(name = "Widget 组件")
@RestController
@RequestMapping("/api/v1/widgets")
public class WidgetController {

    private final WidgetApplicationService widgetService;

    public WidgetController(WidgetApplicationService widgetService) {
        this.widgetService = widgetService;
    }

    @Operation(summary = "分页查询 Widget 列表", operationId = "pageWidgets")
    @RequiresPermission("widget:view")
    @GetMapping
    public Result<PageResult<RptWidget>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String widgetType) {
        PageRequest request = PageRequest.of(page, size);
        return Result.ok(widgetService.page(request, keyword, widgetType), TraceContext.getTraceId());
    }

    @Operation(summary = "查询 Widget 详情", operationId = "getWidget")
    @RequiresPermission("widget:view")
    @GetMapping("/{code}")
    public Result<RptWidget> get(@PathVariable String code) {
        return Result.ok(widgetService.get(code), TraceContext.getTraceId());
    }

    @Operation(summary = "创建 Widget", operationId = "createWidget")
    @RequiresPermission("widget:create")
    @Auditable(operationType = "CREATE", module = "dashboard", bizType = "rpt_widget",
            bizIdExpr = "#result.data.widgetCode", content = "创建 Widget", recordResult = true)
    @PostMapping
    public Result<RptWidget> create(@Valid @RequestBody SaveWidgetRequest request) {
        return Result.ok(widgetService.create(request), TraceContext.getTraceId());
    }

    @Operation(summary = "更新 Widget", operationId = "updateWidget")
    @RequiresPermission("widget:edit")
    @Auditable(operationType = "UPDATE", module = "dashboard", bizType = "rpt_widget",
            bizIdExpr = "#code", content = "更新 Widget")
    @PutMapping("/{code}")
    public Result<RptWidget> update(@PathVariable String code,
                                     @Valid @RequestBody SaveWidgetRequest request) {
        return Result.ok(widgetService.update(code, request), TraceContext.getTraceId());
    }

    @Operation(summary = "删除 Widget", operationId = "deleteWidget")
    @RequiresPermission("widget:delete")
    @Auditable(operationType = "DELETE", module = "dashboard", bizType = "rpt_widget",
            bizIdExpr = "#code", content = "删除 Widget")
    @DeleteMapping("/{code}")
    public Result<Void> delete(@PathVariable String code) {
        widgetService.delete(code);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
