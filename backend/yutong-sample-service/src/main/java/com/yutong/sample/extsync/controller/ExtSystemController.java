package com.yutong.sample.extsync.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.sample.extsync.domain.ExtSystem;
import com.yutong.sample.extsync.dto.SaveExtSystemRequest;
import com.yutong.sample.extsync.service.ExtSystemApplicationService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 外部系统 Controller。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>提供外部系统 CRUD + 列表查询 API。
 */
@RestController
@RequestMapping("/api/v1/ext/systems")
@Tag(name = "ExtSystem", description = "外部接口同步-外部系统")
public class ExtSystemController {

    private final ExtSystemApplicationService systemService;

    public ExtSystemController(ExtSystemApplicationService systemService) {
        this.systemService = systemService;
    }

    @GetMapping
    @Operation(summary = "分页查询外部系统", operationId = "pageExtSystems")
    @RequiresPermission("biz:ext-sync:system:list")
    public Result<Page<ExtSystem>> pageSystems(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String systemCode,
            @RequestParam(required = false) String systemName,
            @RequestParam(required = false) String status) {
        Page<ExtSystem> page = systemService.pageSystems(pageNo, pageSize, systemCode, systemName, status);
        return Result.ok(page, TraceContext.getTraceId());
    }

    @GetMapping("/all")
    @Operation(summary = "查询全部活跃外部系统 (下拉用)", operationId = "listAllExtSystems")
    @RequiresPermission("biz:ext-sync:system:list")
    public Result<List<ExtSystem>> listAll() {
        return Result.ok(systemService.listAll(), TraceContext.getTraceId());
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询外部系统详情", operationId = "getExtSystem")
    @RequiresPermission("biz:ext-sync:system:list")
    public Result<ExtSystem> getSystem(@PathVariable String id) {
        return Result.ok(systemService.getSystem(id), TraceContext.getTraceId());
    }

    @PostMapping
    @Operation(summary = "创建外部系统", operationId = "createExtSystem")
    @RequiresPermission("biz:ext-sync:system:add")
    @Auditable(bizType = "ext-system", module = "sample", bizIdExpr = "#result.data.id", operationType = "CREATE")
    public Result<ExtSystem> createSystem(@Valid @RequestBody SaveExtSystemRequest request) {
        return Result.ok(systemService.createSystem(request), TraceContext.getTraceId());
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新外部系统", operationId = "updateExtSystem")
    @RequiresPermission("biz:ext-sync:system:edit")
    @Auditable(bizType = "ext-system", module = "sample", bizIdExpr = "#id", operationType = "UPDATE")
    public Result<ExtSystem> updateSystem(@PathVariable String id,
                                          @Valid @RequestBody SaveExtSystemRequest request) {
        return Result.ok(systemService.updateSystem(id, request), TraceContext.getTraceId());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除外部系统", operationId = "deleteExtSystem")
    @RequiresPermission("biz:ext-sync:system:delete")
    @Auditable(bizType = "ext-system", module = "sample", bizIdExpr = "#id", operationType = "DELETE")
    public Result<Void> deleteSystem(@PathVariable String id) {
        systemService.deleteSystem(id);
        return Result.ok(null, TraceContext.getTraceId());
    }
}
