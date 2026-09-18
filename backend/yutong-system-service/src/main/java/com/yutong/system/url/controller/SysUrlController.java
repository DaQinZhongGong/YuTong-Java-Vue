package com.yutong.system.url.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.system.url.domain.SysUrl;
import com.yutong.system.url.service.SysUrlService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * URL 白名单管理接口。设计来源: 业界同类实现 SysUrlController + ADR 0005 P3。
 */
@Tag(name = "系统-URL白名单")
@RestController
@RequestMapping("/api/v1/urls")
public class SysUrlController {

    private final SysUrlService service;

    public SysUrlController(SysUrlService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("system:url:list")
    public Result<PageResult<SysUrl>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        Page<SysUrl> page = service.page(pageNo, pageSize, status, keyword);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/enabled")
    @RequiresPermission("system:url:list")
    public Result<List<SysUrl>> listEnabled() {
        return Result.ok(service.listEnabled());
    }

    @GetMapping("/{id}")
    @RequiresPermission("system:url:detail")
    public Result<SysUrl> getById(@PathVariable String id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    @RequiresPermission("system:url:add")
    @Auditable(operationType = "CREATE", module = "system", bizType = "sys_url",
            bizIdExpr = "#result.data", content = "保存URL白名单")
    public Result<String> save(@RequestBody SysUrl url) {
        return Result.ok(service.save(url));
    }

    @PostMapping("/{id}/enable")
    @RequiresPermission("system:url:edit")
    public Result<Void> enable(@PathVariable String id) {
        service.enable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission("system:url:edit")
    public Result<Void> disable(@PathVariable String id) {
        service.disable(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:url:delete")
    @Auditable(operationType = "DELETE", module = "system", bizType = "sys_url",
            bizIdExpr = "#id", content = "删除URL白名单")
    public Result<Void> delete(@PathVariable String id) {
        service.delete(id);
        return Result.ok();
    }
}
