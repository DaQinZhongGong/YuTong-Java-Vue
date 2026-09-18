package com.yutong.system.cms.controller;

import com.yutong.common.response.Result;
import com.yutong.system.cms.domain.CmsContent;
import com.yutong.system.cms.service.CmsContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * CMS 公开内容接口 (无权限码, 所有认证用户可访问)。
 * 落点: CmsContentController 类注释中已承诺但未实现的两个公开端点。
 *
 * 端点:
 *   GET /api/v1/cms/published/{slug}   按 slug 获取已发布内容 (浏览 +1)
 *   GET /api/v1/cms/published           按 category 列出已发布内容 (?category=xxx&limit=20)
 *
 * 安全:
 *   - 仅返回 PUBLISHED 状态内容, DRAFT/ARCHIVED 不可见
 *   - slug 查询命中后浏览计数 +1 (防刷由上游网关限流承担)
 *   - limit 上限 100 (service 层钳制)
 */
@Tag(name = "CMS 公开内容")
@RestController
@RequestMapping("/api/v1/cms/published")
public class CmsPublicController {

    private final CmsContentService service;

    public CmsPublicController(CmsContentService service) {
        this.service = service;
    }

    @GetMapping("/{slug}")
    @Operation(summary = "按 slug 获取已发布内容 (浏览 +1)")
    public Result<CmsContent> getBySlug(@PathVariable("slug") String slug) {
        return Result.ok(service.getPublishedBySlug(slug));
    }

    @GetMapping
    @Operation(summary = "按分类列出已发布内容")
    public Result<List<CmsContent>> listByCategory(
            @RequestParam String category,
            @RequestParam(defaultValue = "20") int limit) {
        return Result.ok(service.listPublishedByCategory(category, limit));
    }
}
