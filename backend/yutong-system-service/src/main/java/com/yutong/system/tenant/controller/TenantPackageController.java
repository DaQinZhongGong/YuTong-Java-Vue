package com.yutong.system.tenant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.system.tenant.domain.SysTenantPackage;
import com.yutong.system.tenant.dto.SaveTenantPackageRequest;
import com.yutong.system.tenant.service.TenantPackageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户套餐管理接口
 * 落点: 70-商业授权与版本能力裁剪 + ADR 0004 P2-F 批次 6-B
 *
 * 端点:
 *   GET  /api/v1/tenant/packages            分页 (管理后台)
 *   GET  /api/v1/tenant/packages/active      列出 ACTIVE (租户订阅)
 *   GET  /api/v1/tenant/packages/{id}        详情
 *   POST /api/v1/tenant/packages            保存 (创建/更新)
 *   POST /api/v1/tenant/packages/{id}/publish  发布
 *   POST /api/v1/tenant/packages/{id}/archive   归档
 */
@Tag(name = "系统-租户套餐管理")
@RestController
@RequestMapping("/api/v1/tenant/packages")
public class TenantPackageController {

    private final TenantPackageService service;

    public TenantPackageController(TenantPackageService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("tenant:package:list")
    public Result<PageResult<SysTenantPackage>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        Page<SysTenantPackage> page = service.page(pageNo, pageSize, status);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/active")
    @Operation(summary = "列出已发布套餐 (供租户订阅)")
    public Result<List<SysTenantPackage>> listActive() {
        return Result.ok(service.listActive());
    }

    @GetMapping("/{id}")
    @RequiresPermission("tenant:package:detail")
    public Result<SysTenantPackage> getById(@PathVariable("id") String id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    @RequiresPermission("tenant:package:save")
    public Result<String> save(@Valid @RequestBody SaveTenantPackageRequest req) {
        return Result.ok(service.save(req));
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission("tenant:package:publish")
    @Operation(summary = "发布套餐 (DRAFT → ACTIVE)")
    public Result<Void> publish(@PathVariable("id") String id) {
        service.publish(id);
        return Result.ok();
    }

    @PostMapping("/{id}/archive")
    @RequiresPermission("tenant:package:archive")
    @Operation(summary = "归档套餐 (任何状态 → ARCHIVED)")
    public Result<Void> archive(@PathVariable("id") String id) {
        service.archive(id);
        return Result.ok();
    }
}
