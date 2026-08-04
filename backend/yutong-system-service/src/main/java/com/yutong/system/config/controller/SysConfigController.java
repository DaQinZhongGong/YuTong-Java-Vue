package com.yutong.system.config.controller;

import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.config.domain.SysConfig;
import com.yutong.system.config.service.SysConfigService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 系统参数配置接口。设计来源: 08-API契约设计、58-后端API逐接口任务清单、91-Web基础后台逐页交互详设
 *
 * <p>GA2-15-6a 修复: 路由路径由 /api/v1/sys-configs 改为 /api/v1/configs，对齐
 * 08/58/91 设计文档与 contracts/openapi/openapi.yaml；refresh-cache 由
 * POST /cache/refresh 改为 PUT /refresh-cache，权限码由 system:config:cache
 * 改为 system:config:refresh-cache，对齐 91 文档权限矩阵。
 */
@Tag(name = "系统参数配置管理")
@RestController
@RequestMapping("/api/v1/configs")
public class SysConfigController {

    private final SysConfigService sysConfigService;

    public SysConfigController(SysConfigService sysConfigService) {
        this.sysConfigService = sysConfigService;
    }

    @Operation(summary = "分页查询参数配置", operationId = "listConfigs")
    @RequiresPermission("system:config:list")
    @GetMapping
    public Result<PageResult<SysConfig>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String configGroup) {
        return Result.ok(sysConfigService.pageConfigs(PageRequest.of(page, size), keyword, configGroup),
                TraceContext.getTraceId());
    }

    @Operation(summary = "创建参数配置", operationId = "createConfig")
    @RequiresPermission("system:config:add")
    @Auditable(operationType = "CREATE", module = "system", bizType = "sys_config",
            bizIdExpr = "#result.data.id", content = "创建参数配置", recordResult = true)
    @PostMapping
    public Result<SysConfig> create(@Valid @RequestBody SysConfig config) {
        return Result.ok(sysConfigService.createConfig(config), TraceContext.getTraceId());
    }

    @Operation(summary = "更新参数配置", operationId = "updateConfig")
    @RequiresPermission("system:config:edit")
    @Auditable(operationType = "UPDATE", module = "system", bizType = "sys_config",
            bizIdExpr = "#id", content = "更新参数配置")
    @PutMapping("/{id}")
    public Result<SysConfig> update(@PathVariable String id, @Valid @RequestBody SysConfig config) {
        return Result.ok(sysConfigService.updateConfig(id, config), TraceContext.getTraceId());
    }

    /**
     * 查询参数配置详情。
     * GA2-15 落地: 补齐管理端"详情查看"接口，对齐 91-Web基础后台逐页交互详设。
     */
    @Operation(summary = "查询参数配置详情", operationId = "getConfig")
    @RequiresPermission("system:config:list")
    @GetMapping("/{id}")
    public Result<SysConfig> get(@PathVariable String id) {
        return Result.ok(sysConfigService.getConfig(id), TraceContext.getTraceId());
    }

    /**
     * 删除参数配置。
     * GA2-15 落地: 补齐管理端"删除"接口，对齐 17-平台基础能力/91-Web基础后台逐页交互详设。
     * 删除后自动清理 Redis 缓存 yutong:config:{configKey}，避免脏读。
     */
    @Operation(summary = "删除参数配置", operationId = "deleteConfig")
    @RequiresPermission("system:config:remove")
    @Auditable(operationType = "DELETE", module = "system", bizType = "sys_config",
            bizIdExpr = "#id", content = "删除参数配置")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        sysConfigService.deleteConfig(id);
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "刷新参数配置缓存", operationId = "refreshConfigCache")
    @RequiresPermission("system:config:refresh-cache")
    @Auditable(operationType = "REFRESH", module = "system", bizType = "sys_config",
            content = "刷新参数配置缓存")
    @PutMapping("/refresh-cache")
    public Result<Void> refreshCache() {
        sysConfigService.refreshConfigCache();
        return Result.ok(null, TraceContext.getTraceId());
    }
}
