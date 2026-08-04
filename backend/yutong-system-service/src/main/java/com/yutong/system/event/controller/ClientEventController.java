package com.yutong.system.event.controller;

import com.yutong.auth.PublicEndpoint;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.event.domain.ClientEvent;
import com.yutong.system.event.dto.ClientEventBatchRequest;
import com.yutong.system.event.service.ClientEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 端侧埋点事件接口。设计来源: 94-端侧埋点与体验监控详设
 *
 * <p>POST /api/v1/client-events/batch — 端侧 SDK 批量上报（PublicEndpoint，支持未登录页面白屏检测）。
 * <p>GET  /api/v1/client-events       — 分页查询（需 monitor:client-event:list 权限，用于验证和看板）。
 */
@Tag(name = "端侧埋点监控")
@RestController
@RequestMapping("/api/v1/client-events")
public class ClientEventController {

    private final ClientEventService clientEventService;

    public ClientEventController(ClientEventService clientEventService) {
        this.clientEventService = clientEventService;
    }

    /**
     * 批量上报端侧埋点事件。
     *
     * <p>设计来源: 94 号文档"上报接口建议为 POST /api/v1/client-events/batch，生产环境默认批量上报"。
     * 标注 {@link PublicEndpoint} 支持未登录页面（如登录页白屏）的事件上报。
     * 单次上限 100 条，超出由 service 层截断。
     */
    @PublicEndpoint
    @Operation(summary = "批量上报端侧埋点事件", operationId = "batchReportClientEvents")
    @PostMapping("/batch")
    public Result<Map<String, Object>> batchReport(@RequestBody ClientEventBatchRequest request) {
        int inserted = clientEventService.batchInsert(request.events());
        return Result.ok(Map.of(
                "inserted", inserted,
                "total", request.events() != null ? request.events().size() : 0
        ), TraceContext.getTraceId());
    }

    /**
     * 分页查询端侧埋点事件（用于验证和看板数据源）。
     * 支持按 eventName/route/platform/result 过滤。
     */
    @Operation(summary = "分页查询端侧埋点事件", operationId = "listClientEvents")
    @RequiresPermission("monitor:client-event:list")
    @GetMapping
    public Result<PageResult<ClientEvent>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String eventName,
            @RequestParam(required = false) String route,
            @RequestParam(required = false) String platform,
            @RequestParam(required = false) String result) {
        return Result.ok(clientEventService.pageEvents(
                PageRequest.of(page, size), eventName, route, platform, result),
                TraceContext.getTraceId());
    }
}
