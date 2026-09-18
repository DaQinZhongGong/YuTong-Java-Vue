package com.yutong.ai.tool.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.tool.domain.AiMcpServer;
import com.yutong.ai.tool.mcp.McpToolDescriptor;
import com.yutong.ai.tool.service.McpClientRegistry;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * MCP 服务注册接口。设计来源: V038 Phase 3 Tool/MCP
 */
@Tag(name = "AI-MCP服务管理")
@RestController
@RequestMapping("/api/v1/mcp/servers")
public class McpServerController {

    private final McpClientRegistry registry;
    private final ObjectMapper objectMapper;

    public McpServerController(McpClientRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Operation(summary = "分页查询 MCP 服务", operationId = "pageMcpServers")
    @RequiresPermission("ai:mcp:list")
    @GetMapping
    public Result<PageResult<AiMcpServer>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String serverCode,
            @RequestParam(required = false) String transport,
            @RequestParam(required = false) Boolean enabled) {
        return Result.ok(registry.pageServers(PageRequest.of(page, size), serverCode, transport, enabled),
                TraceContext.getTraceId());
    }

    @Operation(summary = "查询 MCP 服务详情", operationId = "getMcpServer")
    @RequiresPermission("ai:mcp:detail")
    @GetMapping("/{id}")
    public Result<AiMcpServer> get(@PathVariable String id) {
        return Result.ok(registry.getServer(id), TraceContext.getTraceId());
    }

    @Operation(summary = "列出已启用 MCP 服务 (供 LLM 工具路由)", operationId = "listEnabledMcpServers")
    @RequiresPermission("ai:mcp:list")
    @GetMapping("/enabled")
    public Result<List<AiMcpServer>> listEnabled() {
        return Result.ok(registry.listEnabled(), TraceContext.getTraceId());
    }

    @Operation(summary = "创建或更新 MCP 服务", operationId = "saveMcpServer")
    @RequiresPermission("ai:mcp:add")
    @PostMapping
    public Result<AiMcpServer> save(@RequestBody AiMcpServer server) {
        return Result.ok(registry.saveServer(server), TraceContext.getTraceId());
    }

    @Operation(summary = "更新 MCP 服务", operationId = "updateMcpServer")
    @RequiresPermission("ai:mcp:edit")
    @PutMapping("/{id}")
    public Result<AiMcpServer> update(@PathVariable String id, @RequestBody AiMcpServer server) {
        server.setId(id);
        return Result.ok(registry.saveServer(server), TraceContext.getTraceId());
    }

    @Operation(summary = "启用 MCP 服务", operationId = "enableMcpServer")
    @RequiresPermission("ai:mcp:edit")
    @PostMapping("/{id}/enable")
    public Result<AiMcpServer> enable(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(registry.enable(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "禁用 MCP 服务", operationId = "disableMcpServer")
    @RequiresPermission("ai:mcp:edit")
    @PostMapping("/{id}/disable")
    public Result<AiMcpServer> disable(@PathVariable String id, @RequestParam Integer version) {
        return Result.ok(registry.disable(id, version), TraceContext.getTraceId());
    }

    @Operation(summary = "MCP 服务健康检查 (真实 list_tools)", operationId = "healthCheckMcpServer")
    @RequiresPermission("ai:mcp:detail")
    @PostMapping("/{id}/health-check")
    public Result<AiMcpServer> healthCheck(@PathVariable String id) {
        return Result.ok(registry.healthCheck(id), TraceContext.getTraceId());
    }

    @Operation(summary = "MCP 列出工具 (真实 list_tools，带缓存)", operationId = "listMcpTools")
    @RequiresPermission("ai:mcp:list")
    @GetMapping("/{id}/tools")
    public Result<List<McpToolDescriptor>> listTools(@PathVariable String id) {
        return Result.ok(registry.listTools(id), TraceContext.getTraceId());
    }

    @Operation(summary = "MCP 调用工具 (真实 tool/call，审计入参出参/latency)", operationId = "callMcpTool")
    @RequiresPermission("ai:mcp:detail")
    @PostMapping("/{id}/tools/{toolName}/call")
    public Result<Map<String, Object>> callTool(@PathVariable String id,
                                                @PathVariable String toolName,
                                                @RequestBody(required = false) Map<String, Object> args) {
        String argsJson = null;
        try {
            if (args != null) argsJson = objectMapper.writeValueAsString(args);
        } catch (Exception e) {
            argsJson = args != null ? args.toString() : null;
        }
        String output = registry.callTool(id, toolName, argsJson);
        return Result.ok(Map.of("tool", toolName, "output", output), TraceContext.getTraceId());
    }

    @Operation(summary = "MCP 真实连接并缓存工具 (connect)", operationId = "connectMcpServer")
    @RequiresPermission("ai:mcp:detail")
    @PostMapping("/{id}/connect")
    public Result<List<McpToolDescriptor>> connect(@PathVariable String id) {
        var server = registry.getServer(id);
        return Result.ok(registry.connect(server), TraceContext.getTraceId());
    }

    /**
     * MCP 服务测试 — 健康检查 + 工具列表一步到位。
     * MCP 工具连通性测试与状态探针。
     * 返回: {healthy, toolCount, tools, error}
     */
    @Operation(summary = "MCP 服务测试 (健康检查+工具列表)", operationId = "testMcpServer")
    @RequiresPermission("ai:mcp:detail")
    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> test(@PathVariable String id) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        try {
            var server = registry.healthCheck(id);
            List<McpToolDescriptor> tools = registry.listTools(id);
            data.put("healthy", true);
            data.put("serverCode", server.getServerCode());
            data.put("transport", server.getTransport());
            data.put("toolCount", tools.size());
            data.put("tools", tools);
            data.put("error", null);
        } catch (Exception e) {
            data.put("healthy", false);
            data.put("toolCount", 0);
            data.put("tools", List.of());
            data.put("error", e.getMessage() != null ? e.getMessage() : "unknown");
        }
        return Result.ok(data, TraceContext.getTraceId());
    }
}
