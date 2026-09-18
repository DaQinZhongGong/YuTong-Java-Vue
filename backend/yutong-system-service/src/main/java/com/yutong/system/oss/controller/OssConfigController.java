package com.yutong.system.oss.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.system.oss.domain.SysOssConfig;
import com.yutong.system.oss.dto.SaveOssConfigRequest;
import com.yutong.system.oss.service.OssConfigService;
import com.yutong.system.log.auditable.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * OSS 配置管理接口。
 * 落点: 业界同类实现 SysOssConfigController + ADR 0005 P1-D。
 *
 * <p>端点:
 * <ul>
 *   <li>GET /api/v1/oss/configs — 分页 (status/keyword, secretKey 脱敏)</li>
 *   <li>GET /api/v1/oss/configs/enabled — 全部启用配置 (脱敏)</li>
 *   <li>GET /api/v1/oss/configs/{id} — 详情 (含 secretKey)</li>
 *   <li>POST /api/v1/oss/configs — 保存 (创建/更新)</li>
 *   <li>POST /api/v1/oss/configs/{id}/enable — 启用</li>
 *   <li>POST /api/v1/oss/configs/{id}/disable — 停用 (默认禁止)</li>
 *   <li>POST /api/v1/oss/configs/{id}/default — 设为默认</li>
 *   <li>POST /api/v1/oss/configs/{id}/test — 测试连接</li>
 *   <li>DELETE /api/v1/oss/configs/{id} — 删除 (默认禁止)</li>
 * </ul>
 */
@Tag(name = "系统-OSS配置")
@RestController
@RequestMapping("/api/v1/oss/configs")
public class OssConfigController {

    private final OssConfigService service;

    public OssConfigController(OssConfigService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("system:oss-config:list")
    public Result<PageResult<SysOssConfig>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        Page<SysOssConfig> page = service.page(pageNo, pageSize, status, keyword);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(),
                (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/enabled")
    @RequiresPermission("system:oss-config:list")
    public Result<List<SysOssConfig>> listEnabled() {
        return Result.ok(service.listEnabled());
    }

    @GetMapping("/{id}")
    @RequiresPermission("system:oss-config:detail")
    public Result<SysOssConfig> getById(@PathVariable("id") String id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    @RequiresPermission("system:oss-config:save")
    @Auditable(operationType = "CREATE", module = "system", bizType = "sys_oss_config",
            bizIdExpr = "#result.data", content = "保存OSS配置")
    public Result<String> save(@Valid @RequestBody SaveOssConfigRequest req) {
        return Result.ok(service.save(req));
    }

    @PostMapping("/{id}/enable")
    @RequiresPermission("system:oss-config:enable")
    @Operation(summary = "启用配置")
    public Result<Void> enable(@PathVariable("id") String id) {
        service.enable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/disable")
    @RequiresPermission("system:oss-config:disable")
    @Operation(summary = "停用配置 (默认配置禁止)")
    public Result<Void> disable(@PathVariable("id") String id) {
        service.disable(id);
        return Result.ok();
    }

    @PostMapping("/{id}/default")
    @RequiresPermission("system:oss-config:save")
    @Operation(summary = "设为默认配置")
    public Result<Void> setDefault(@PathVariable("id") String id) {
        service.setDefault(id);
        return Result.ok();
    }

    @PostMapping("/{id}/test")
    @RequiresPermission("system:oss-config:detail")
    @Operation(summary = "测试连接 (真实探测 bucketExists)")
    public Result<Boolean> testConnection(@PathVariable("id") String id) {
        return Result.ok(service.testConnection(id));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("system:oss-config:delete")
    @Auditable(operationType = "DELETE", module = "system", bizType = "sys_oss_config",
            bizIdExpr = "#id", content = "删除OSS配置")
    public Result<Void> delete(@PathVariable("id") String id) {
        service.delete(id);
        return Result.ok();
    }
}
