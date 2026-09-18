package com.yutong.system.tenant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.system.tenant.domain.SysTenant;
import com.yutong.system.tenant.dto.SaveTenantRequest;
import com.yutong.system.tenant.service.SysTenantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 租户注册表管理接口 (平台管理员视角, 全局)。
 * 落点: 业界同类实现 SysTenantController + ADR 0004 P2-F。
 *
 * <p>端点:
 * <ul>
 *   <li>GET /api/v1/tenants — 分页 (status/keyword)</li>
 *   <li>GET /api/v1/tenants/{id} — 详情</li>
 *   <li>POST /api/v1/tenants — 保存 (创建/更新, 编码创建后不可改)</li>
 *   <li>POST /api/v1/tenants/{id}/enable — 启用</li>
 *   <li>POST /api/v1/tenants/{id}/disable — 停用 (default 禁止)</li>
 *   <li>POST /api/v1/tenants/{id}/assign-package — 分配套餐 (packageId + expireTime 可空)</li>
 *   <li>DELETE /api/v1/tenants/{id} — 删除 (仅 DISABLED, default 禁止)</li>
 * </ul>
 *
 * <p>安全: sys_tenant 豁免行级过滤 (见 application.yml yutong.tenant.ignore-tables),
 * 全接口由 {@code tenant:tenant:*} 权限码守卫, 仅平台管理员持有。
 */
@Tag(name = "系统-租户管理")
@RestController
@RequestMapping("/api/v1/tenants")
public class SysTenantController {

    private final SysTenantService service;

    public SysTenantController(SysTenantService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("tenant:tenant:list")
    public Result<PageResult<SysTenant>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        Page<SysTenant> page = service.page(pageNo, pageSize, status, keyword);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/{id}")
    @RequiresPermission("tenant:tenant:detail")
    public Result<SysTenant> getById(@PathVariable("id") String id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    @RequiresPermission("tenant:tenant:save")
    public Result<String> save(@Valid @RequestBody SaveTenantRequest req) {
        return Result.ok(service.save(req));
    }

    @PostMapping("/{id}/enable")
    @RequiresPermission("tenant:tenant:enable")
    @Operation(summary = "启用租户 (DISABLED → NORMAL)")
    public Result<Void> enable(@PathVariable("id") String id) {
        service.enable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission("tenant:tenant:disable")
    @Operation(summary = "停用租户 (NORMAL → DISABLED, default 禁止)")
    public Result<Void> disable(@PathVariable("id") String id) {
        service.disable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/assign-package")
    @RequiresPermission("tenant:tenant:assign")
    @Operation(summary = "分配套餐 (校验套餐存在且非归档)")
    public Result<Void> assignPackage(
            @PathVariable("id") String id,
            @RequestParam String packageId,
            @RequestParam(required = false) LocalDateTime expireTime) {
        service.assignPackage(id, packageId, expireTime);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("tenant:tenant:delete")
    @Operation(summary = "删除租户 (仅 DISABLED, default 禁止)")
    public Result<Void> delete(@PathVariable("id") String id) {
        service.delete(id);
        return Result.ok();
    }
}
