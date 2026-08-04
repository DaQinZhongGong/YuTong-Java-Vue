package com.yutong.sample.workbench.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.workbench.dto.WorkbenchStatsVO;
import com.yutong.sample.workbench.dto.WorkbenchTopItemVO;
import com.yutong.sample.workbench.dto.WorkbenchTrendVO;
import com.yutong.sample.workbench.service.WorkbenchApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工作台 Controller。设计来源: 18-样例业务详细设计 工作台统计、19-页面原型与站点地图、42-报表与大屏可视化设计 R0。
 */
@RestController
@RequestMapping("/api/v1/workbench")
@Tag(name = "Workbench", description = "工作台统计")
public class WorkbenchController {

    private final WorkbenchApplicationService workbenchService;

    public WorkbenchController(WorkbenchApplicationService workbenchService) {
        this.workbenchService = workbenchService;
    }

    @GetMapping("/stats")
    @Operation(summary = "获取工作台统计数据", operationId = "getDashboardStats", description = "聚合客户/商品/申请单/待办/消息统计")
    @RequiresPermission("dashboard:view")
    public Result<WorkbenchStatsVO> getStats() {
        return Result.ok(workbenchService.getStats(), TraceContext.getTraceId());
    }

    /**
     * GA2-33: 近 N 日申请单提交趋势。对齐 42 号文档 biz_request_trend_7d 数据集。
     * 前端 ECharts 折线图数据源。
     */
    @GetMapping("/trend")
    @Operation(summary = "获取近 N 日申请单提交趋势", operationId = "getWorkbenchTrend",
            description = "按日聚合申请单提交数和金额合计，对齐 42 号文档 biz_request_trend_7d 数据集")
    @RequiresPermission("dashboard:view")
    public Result<WorkbenchTrendVO> getTrend(
            @Parameter(description = "天数，默认 7，上限 90") @RequestParam(defaultValue = "7") int days) {
        return Result.ok(workbenchService.getRecentTrend(days));
    }

    /**
     * GA2-33: 金额 Top N 申请单。对齐 42 号文档 biz_request_amount_top10 数据集。
     * 前端 ECharts 柱状图数据源。
     */
    @GetMapping("/top-amount")
    @Operation(summary = "获取金额 Top N 申请单", operationId = "getWorkbenchTopAmount",
            description = "按金额降序取前 N，排除草稿，对齐 42 号文档 biz_request_amount_top10 数据集")
    @RequiresPermission("dashboard:view")
    public Result<List<WorkbenchTopItemVO>> getTopAmount(
            @Parameter(description = "条数，默认 10，上限 50") @RequestParam(defaultValue = "10") int limit) {
        return Result.ok(workbenchService.getAmountTop(limit));
    }
}
