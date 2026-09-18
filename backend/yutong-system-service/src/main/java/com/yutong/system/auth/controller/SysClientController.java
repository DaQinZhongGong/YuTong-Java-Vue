package com.yutong.system.auth.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.system.auth.domain.SysClient;
import com.yutong.system.auth.service.SysClientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OAuth2 客户端管理接口
 * 落点: 08-API 契约设计 + ADR 0004 P2-F 批次 6-C
 *
 * 端点:
 *   GET  /api/v1/oauth/clients                    分页
 *   GET  /api/v1/oauth/clients/{id}              详情
 *   POST /api/v1/oauth/clients                    保存 (创建/更新)
 *   POST /api/v1/oauth/clients/{id}/enable        启用
 *   POST /api/v1/oauth/clients/{id}/disable       禁用
 *   POST /api/v1/oauth/clients/{id}/reset-secret  重置密钥 (返回新密钥, 仅 1 次可见)
 */
@Tag(name = "系统-OAuth 客户端管理")
@RestController
@RequestMapping("/api/v1/oauth/clients")
public class SysClientController {

    private final SysClientService service;

    public SysClientController(SysClientService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("auth:client:list")
    public Result<PageResult<SysClient>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status) {
        Page<SysClient> page = service.page(pageNo, pageSize, status);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/{id}")
    @RequiresPermission("auth:client:detail")
    public Result<SysClient> getById(@PathVariable("id") String id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    @RequiresPermission("auth:client:save")
    public Result<String> save(@RequestBody SysClient c) {
        return Result.ok(service.save(c));
    }

    @PostMapping("/{id}/enable")
    @RequiresPermission("auth:client:save")
    public Result<Void> enable(@PathVariable("id") String id) {
        service.enable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission("auth:client:save")
    public Result<Void> disable(@PathVariable("id") String id) {
        service.disable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/reset-secret")
    @RequiresPermission("auth:client:save")
    @Operation(summary = "重置 clientSecret, 返回新密钥 (仅本次响应可见, 不存明文)")
    public Result<Map<String, String>> resetSecret(@PathVariable("id") String id) {
        String newSecret = service.resetSecret(id);
        return Result.ok(Map.of("clientSecret", newSecret));
    }
}
