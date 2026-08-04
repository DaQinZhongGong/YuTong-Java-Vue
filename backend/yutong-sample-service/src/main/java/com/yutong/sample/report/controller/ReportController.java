package com.yutong.sample.report.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.report.domain.RptDataset;
import com.yutong.sample.report.domain.RptReport;
import com.yutong.sample.report.dto.DatasetResultVO;
import com.yutong.sample.report.dto.ReportExplainVO;
import com.yutong.sample.report.dto.ReportRenderVO;
import com.yutong.sample.report.dto.SaveDatasetRequest;
import com.yutong.sample.report.dto.SaveReportRequest;
import com.yutong.sample.report.service.ReportApplicationService;
import com.yutong.system.log.auditable.Auditable;
import com.yutong.system.log.domain.SysImportExportTask;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 报表分析 Controller。设计来源: 35-样例业务矩阵扩展设计 P1 报表分析、42-报表与大屏可视化设计 R1。
 *
 * <p>GA2-36 验证 5 项能力的 API 端点：
 * <ul>
 *   <li>物化视图刷新：POST /materialized-views/{name}/refresh</li>
 *   <li>ECharts 图表：GET /reports/{code}/render</li>
 *   <li>大数据量导出异步化：POST /reports/{code}/export</li>
 *   <li>AI 指标解释：POST /reports/{code}/explain</li>
 *   <li>数据权限下的报表过滤：复用 DatasetEngine 的 DataScope 注入 + 列级脱敏</li>
 * </ul>
 *
 * <p>权限码：报表查看 report:view、数据集查看 report:dataset:view。
 * 通过 Mock 4 类用户切换验证不同角色的脱敏和过滤行为。
 */
@RestController
@RequestMapping("/api/v1/report")
@Tag(name = "Report", description = "报表分析")
public class ReportController {

    private final ReportApplicationService reportService;

    public ReportController(ReportApplicationService reportService) {
        this.reportService = reportService;
    }

    // ==================== 数据集 ====================

    @GetMapping("/datasets")
    @Operation(summary = "分页查询数据集列表", operationId = "pageDatasets")
    @RequiresPermission("report:view")
    public Result<PageResult<RptDataset>> pageDatasets(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String datasetCode,
            @RequestParam(required = false) String datasetName,
            @RequestParam(required = false) String status) {
        PageResult<RptDataset> result = reportService.pageDatasets(
                PageRequest.of(page, size), datasetCode, datasetName, status);
        return Result.ok(result, TraceContext.getTraceId());
    }

    @GetMapping("/datasets/{code}")
    @Operation(summary = "查询数据集详情", operationId = "getDataset")
    @RequiresPermission("report:view")
    public Result<RptDataset> getDataset(@PathVariable String code) {
        return Result.ok(reportService.getDataset(code), TraceContext.getTraceId());
    }

    @PostMapping("/datasets")
    @Operation(summary = "创建数据集", operationId = "createDataset")
    @RequiresPermission("report:view")
    @Auditable(operationType = "CREATE", module = "report", bizType = "rpt_dataset",
            bizIdExpr = "#result.data.id", content = "创建数据集", recordResult = true)
    public Result<RptDataset> createDataset(@Valid @RequestBody SaveDatasetRequest request) {
        return Result.ok(reportService.createDataset(request), TraceContext.getTraceId());
    }

    @PutMapping("/datasets/{code}")
    @Operation(summary = "更新数据集", operationId = "updateDataset")
    @RequiresPermission("report:view")
    @Auditable(operationType = "UPDATE", module = "report", bizType = "rpt_dataset",
            bizIdExpr = "#code", content = "更新数据集")
    public Result<RptDataset> updateDataset(@PathVariable String code,
                                              @Valid @RequestBody SaveDatasetRequest request) {
        return Result.ok(reportService.updateDataset(code, request), TraceContext.getTraceId());
    }

    @DeleteMapping("/datasets/{code}")
    @Operation(summary = "删除数据集（被报表引用不可删）", operationId = "deleteDataset")
    @RequiresPermission("report:view")
    @Auditable(operationType = "DELETE", module = "report", bizType = "rpt_dataset",
            bizIdExpr = "#code", content = "删除数据集")
    public Result<Void> deleteDataset(@PathVariable String code) {
        reportService.deleteDataset(code);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @PostMapping("/datasets/{code}/preview")
    @Operation(summary = "预览数据集（100 行限制，不缓存，不走 DataScope）",
            operationId = "previewDataset",
            description = "用于数据集编辑时实时预览效果，不写入缓存，不应用数据权限过滤")
    @RequiresPermission("report:view")
    public Result<DatasetResultVO> previewDataset(@PathVariable String code,
                                                    @RequestBody(required = false) Map<String, Object> params) {
        return Result.ok(reportService.preview(code, params), TraceContext.getTraceId());
    }

    // ==================== 报表 ====================

    @GetMapping("/reports")
    @Operation(summary = "分页查询报表列表", operationId = "pageReports")
    @RequiresPermission("report:view")
    public Result<PageResult<RptReport>> pageReports(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String reportCode,
            @RequestParam(required = false) String reportName,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String status) {
        PageResult<RptReport> result = reportService.pageReports(
                PageRequest.of(page, size), reportCode, reportName, reportType, status);
        return Result.ok(result, TraceContext.getTraceId());
    }

    @GetMapping("/reports/{code}")
    @Operation(summary = "查询报表详情", operationId = "getReport")
    @RequiresPermission("report:view")
    public Result<RptReport> getReport(@PathVariable String code) {
        return Result.ok(reportService.getReport(code), TraceContext.getTraceId());
    }

    @PostMapping("/reports")
    @Operation(summary = "创建报表", operationId = "createReport")
    @RequiresPermission("report:view")
    @Auditable(operationType = "CREATE", module = "report", bizType = "rpt_report",
            bizIdExpr = "#result.data.id", content = "创建报表", recordResult = true)
    public Result<RptReport> createReport(@Valid @RequestBody SaveReportRequest request) {
        return Result.ok(reportService.createReport(request), TraceContext.getTraceId());
    }

    @PutMapping("/reports/{code}")
    @Operation(summary = "更新报表（layout_json 变更会自增 versionNo）", operationId = "updateReport")
    @RequiresPermission("report:view")
    @Auditable(operationType = "UPDATE", module = "report", bizType = "rpt_report",
            bizIdExpr = "#code", content = "更新报表")
    public Result<RptReport> updateReport(@PathVariable String code,
                                            @Valid @RequestBody SaveReportRequest request) {
        return Result.ok(reportService.updateReport(code, request), TraceContext.getTraceId());
    }

    @DeleteMapping("/reports/{code}")
    @Operation(summary = "删除报表", operationId = "deleteReport")
    @RequiresPermission("report:view")
    @Auditable(operationType = "DELETE", module = "report", bizType = "rpt_report",
            bizIdExpr = "#code", content = "删除报表")
    public Result<Void> deleteReport(@PathVariable String code) {
        reportService.deleteReport(code);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @GetMapping("/reports/{code}/render")
    @Operation(summary = "渲染报表（返回 layoutJson + 各组件数据集执行结果，前端 ECharts 渲染）",
            operationId = "renderReport",
            description = "对每个 component 调用 DatasetEngine.execute，返回 componentData Map。"
                    + "支持通过 query params 传递数据集参数，如 ?days=7")
    @RequiresPermission("report:view")
    public Result<ReportRenderVO> renderReport(@PathVariable String code,
                                                 @RequestParam(required = false) Map<String, Object> params) {
        return Result.ok(reportService.render(code, params), TraceContext.getTraceId());
    }

    @PostMapping("/reports/{code}/export")
    @Operation(summary = "异步导出报表（PENDING → 异步生成 → SUCCESS/FAILED）",
            operationId = "exportReport",
            description = "创建 sys_import_export_task（PENDING），异步执行各组件数据集查询并累加行数。"
                    + "前端通过 /api/v1/import-export-tasks/{id} 轮询任务状态。")
    @RequiresPermission("report:view")
    @Auditable(operationType = "EXPORT", module = "report", bizType = "rpt_report",
            bizIdExpr = "#code", content = "异步导出报表")
    public Result<SysImportExportTask> exportReport(@PathVariable String code,
                                                      @RequestBody(required = false) Map<String, Object> params) {
        return Result.ok(reportService.export(code, params), TraceContext.getTraceId());
    }

    @PostMapping("/reports/{code}/explain")
    @Operation(summary = "AI 指标解释（降级模式：返回结构化数据摘要）",
            operationId = "explainReport",
            description = "v1.0 降级模式（degraded=true）：基于报表数据生成模板化解释，不依赖 AI 提供商。"
                    + "v1.1+ 接入真实 AI 提供商后，将基于数据趋势识别异常波动并生成自然语言解读。")
    @RequiresPermission("report:view")
    @Auditable(operationType = "EXPLAIN", module = "report", bizType = "rpt_report",
            bizIdExpr = "#code", content = "AI 指标解释")
    public Result<ReportExplainVO> explainReport(@PathVariable String code) {
        return Result.ok(reportService.explain(code), TraceContext.getTraceId());
    }

    // ==================== 物化视图 ====================

    @PostMapping("/materialized-views/{name}/refresh")
    @Operation(summary = "刷新物化视图（白名单：rpt_mv_request_status_stat / rpt_mv_request_trend_7d）",
            operationId = "refreshMaterializedView",
            description = "执行 REFRESH MATERIALIZED VIEW，更新物化视图数据。"
                    + "白名单校验防 SQL 注入，仅允许刷新预定义的物化视图。")
    @RequiresPermission("report:view")
    @Auditable(operationType = "REFRESH", module = "report", bizType = "rpt_materialized_view",
            bizIdExpr = "#name", content = "刷新物化视图")
    public Result<Void> refreshMaterializedView(@PathVariable String name) {
        reportService.refreshMaterializedView(name);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @GetMapping("/materialized-views/{name}/data")
    @Operation(summary = "查询物化视图数据（按当前租户过滤）", operationId = "queryMaterializedView")
    @RequiresPermission("report:view")
    public Result<List<Map<String, Object>>> queryMaterializedView(@PathVariable String name) {
        return Result.ok(reportService.queryMaterializedView(name), TraceContext.getTraceId());
    }
}
