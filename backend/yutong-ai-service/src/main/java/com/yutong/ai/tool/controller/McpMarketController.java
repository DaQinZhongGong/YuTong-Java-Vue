package com.yutong.ai.tool.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.ai.tool.domain.AiMcpMarket;
import com.yutong.ai.tool.domain.AiMcpMarketTool;
import com.yutong.ai.tool.service.McpMarketService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 市场接口。设计来源: V043 P0 平价能力 — market list/get/install/sync
 */
@Tag(name = "AI-MCP市场")
@RestController
@RequestMapping("/api/v1/mcp/market")
public class McpMarketController {

    private final McpMarketService marketService;

    public McpMarketController(McpMarketService marketService) {
        this.marketService = marketService;
    }

    @Operation(summary = "分页查询 MCP 市场", operationId = "pageMcpMarket")
    @RequiresPermission("ai:mcp:list")
    @GetMapping
    public Result<PageResult<AiMcpMarket>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String status) {
        return Result.ok(marketService.list(PageRequest.of(page, size), keyword, category, status),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询 MCP 市场详情", operationId = "getMcpMarket")
    @RequiresPermission("ai:mcp:detail")
    @GetMapping("/{id}")
    public Result<AiMcpMarket> get(@PathVariable String id) {
        return Result.ok(marketService.get(id), TraceContext.getTraceId());
    }

    @Operation(summary = "查询 MCP 市场工具列表", operationId = "listMcpMarketTools")
    @RequiresPermission("ai:mcp:list")
    @GetMapping("/{id}/tools")
    public Result<List<AiMcpMarketTool>> listTools(@PathVariable String id) {
        return Result.ok(marketService.listTools(id), TraceContext.getTraceId());
    }

    @Operation(summary = "安装 MCP 市场条目（幂等 install_count+1）", operationId = "installMcpMarket")
    @RequiresPermission("ai:mcp:add")
    @Auditable(operationType = "INSTALL", module = "ai", bizType = "ai_mcp_market", bizIdExpr = "#id", content = "安装 MCP 市场条目")
    @PostMapping("/{id}/install")
    public Result<AiMcpMarket> install(@PathVariable String id) {
        return Result.ok(marketService.install(id), TraceContext.getTraceId());
    }

    @Operation(summary = "同步 MCP 市场（占位）", operationId = "syncMcpMarket")
    @RequiresPermission("ai:mcp:add")
    @Auditable(operationType = "SYNC", module = "ai", bizType = "ai_mcp_market", content = "同步 MCP 市场")
    @PostMapping("/sync")
    public Result<Map<String, Object>> sync() {
        marketService.sync();
        return Result.ok(Map.of("synced", true), TraceContext.getTraceId());
    }
}
