package com.yutong.system.cms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.PageResult;
import com.yutong.common.response.Result;
import com.yutong.system.cms.domain.CmsContent;
import com.yutong.system.cms.dto.SaveCmsContentRequest;
import com.yutong.system.cms.service.CmsContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * CMS 内容管理接口
 * 落点: 84-CMS 运营详设
 *
 * 端点:
 *   GET  /api/v1/cms/contents            分页查询 (管理后台)
 *   GET  /api/v1/cms/contents/{id}        详情 (管理后台)
 *   POST /api/v1/cms/contents            保存 (创建/更新)
 *   POST /api/v1/cms/contents/{id}/publish  发布
 *   POST /api/v1/cms/contents/{id}/archive   归档
 *   DELETE /api/v1/cms/contents/{id}     物理删除 (仅 DRAFT)
 *
 *   公开端点 (无权限码):
 *   GET  /api/v1/cms/published/{slug}    按 slug 获取已发布内容 (浏览 +1)
 *   GET  /api/v1/cms/published           按 category 列出已发布内容
 */
@Tag(name = "系统-CMS 内容管理")
@RestController
@RequestMapping("/api/v1/cms/contents")
public class CmsContentController {

    private final CmsContentService service;

    public CmsContentController(CmsContentService service) {
        this.service = service;
    }

    @GetMapping
    @RequiresPermission("cms:content:list")
    public Result<PageResult<CmsContent>> page(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String keyword) {
        Page<CmsContent> page = service.page(pageNo, pageSize, status, category, keyword);
        return Result.ok(PageResult.of(page.getRecords(), page.getTotal(), (int) page.getCurrent(), (int) page.getSize()));
    }

    @GetMapping("/{id}")
    @RequiresPermission("cms:content:detail")
    public Result<CmsContent> getById(@PathVariable("id") String id) {
        return Result.ok(service.getById(id));
    }

    @PostMapping
    @RequiresPermission("cms:content:save")
    public Result<String> save(@Valid @RequestBody SaveCmsContentRequest req) {
        return Result.ok(service.save(req));
    }

    @PostMapping("/{id}/publish")
    @RequiresPermission("cms:content:publish")
    @Operation(summary = "发布内容 (DRAFT → PUBLISHED)")
    public Result<Void> publish(@PathVariable("id") String id) {
        service.publish(id);
        return Result.ok();
    }

    @PostMapping("/{id}/archive")
    @RequiresPermission("cms:content:archive")
    @Operation(summary = "归档内容 (PUBLISHED → ARCHIVED)")
    public Result<Void> archive(@PathVariable("id") String id) {
        service.archive(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("cms:content:delete")
    @Operation(summary = "物理删除 (仅 DRAFT 状态允许)")
    public Result<Void> delete(@PathVariable("id") String id) {
        service.hardDelete(id);
        return Result.ok();
    }
}
