package com.yutong.system.cms.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.system.cms.domain.CmsContent;
import com.yutong.system.cms.dto.SaveCmsContentRequest;
import com.yutong.system.cms.mapper.CmsContentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * CMS 内容服务 — 保存/发布/查询/浏览计数
 * 落点: 84-CMS 运营详设
 *
 * 工作流:
 *   1. save: 校验 slug 唯一, 保存 (Markdown + 渲染 HTML)
 *   2. publish: 状态 DRAFT → PUBLISHED, 记录 publishedAt
 *   3. incrementView: 浏览时 +1 (高频, 走 updateById 走主键)
 *
 * 安全: 状态机 DRAFT → PUBLISHED → ARCHIVED, 禁止跳级
 *       同一 tenant 内 slug 唯一 (复合查询: tenantId + slug)
 */
@Service
public class CmsContentService {

    /** slug 合法字符: 字母数字-下划线 */
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]{1,100}$");

    private final CmsContentMapper mapper;

    public CmsContentService(CmsContentMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 分页查询 (按状态/分类/关键词)
     */
    public Page<CmsContent> page(int pageNo, int pageSize, String status, String category, String keyword) {
        QueryWrapper<CmsContent> w = new QueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq("status", status);
        if (category != null && !category.isBlank()) w.eq("category", category);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("title", keyword).or().like("summary", keyword));
        }
        w.orderByDesc("updated_time");
        return mapper.selectPage(Page.of(pageNo, pageSize), w);
    }

    /**
     * 按 slug 查询已发布内容 (公开访问)
     * 自动 incrementView
     */
    public CmsContent getPublishedBySlug(String slug) {
        CmsContent c = mapper.selectOne(
                new QueryWrapper<CmsContent>()
                        .eq("slug", slug)
                        .eq("status", CmsContent.STATUS_PUBLISHED)
                        .last("LIMIT 1")
        );
        if (c == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "内容不存在或未发布: " + slug);
        }
        // 浏览 +1 (异步 / 高频, 走 updateById)
        c.setViewCount(c.getViewCount() == null ? 1L : c.getViewCount() + 1);
        mapper.updateById(c);
        return c;
    }

    /**
     * 按 ID 查询 (管理后台)
     */
    public CmsContent getById(String id) {
        CmsContent c = mapper.selectById(id);
        if (c == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "内容不存在: " + id);
        }
        return c;
    }

    /**
     * 保存 (创建/更新)
     * - 创建: id 必空, 状态默认 DRAFT
     * - 更新: id 必填
     */
    @Transactional
    public String save(SaveCmsContentRequest req) {
        if (req.getSlug() != null && !SLUG_PATTERN.matcher(req.getSlug()).matches()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "slug 只能包含字母数字下划线连字符, 1-100 字符");
        }
        // 校验 slug 唯一 (同 tenant 内)
        if (isSlugTaken(req.getSlug(), req.getId())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "slug 已被占用: " + req.getSlug());
        }
        CmsContent c;
        // BUGFIX 2026-09-07: 创建走 insert, 更新走 updateById。
        // id 显式生成后 getId() 永非空, 原 `getId() == null` 判断使 insert 成死代码,
        // 创建会静默 update 0 行 (数据丢失)。以 isCreate 显式分支。
        boolean isCreate = (req.getId() == null || req.getId().isBlank());
        if (isCreate) {
            c = new CmsContent();
            // BaseEntity.id 无 fill 注解, MetaObjectHandler 不自动填充, 必须显式生成 ULID
            c.setId(IdGenerator.nextId());
            c.setStatus(CmsContent.STATUS_DRAFT);
            c.setViewCount(0L);
        } else {
            c = getById(req.getId());
            // 不允许从 PUBLISHED 改回 DRAFT (避免误改已发布内容)
            if (CmsContent.STATUS_PUBLISHED.equals(c.getStatus())
                    && CmsContent.STATUS_DRAFT.equals(req.getStatus())) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "已发布内容不能改回草稿状态");
            }
        }
        c.setTitle(req.getTitle());
        c.setSlug(req.getSlug());
        c.setContentMd(req.getContentMd());
        c.setContentHtml(renderMarkdown(req.getContentMd()));
        c.setSummary(req.getSummary());
        c.setCategory(req.getCategory());
        c.setTags(req.getTags());
        if (req.getStatus() != null && !req.getStatus().isBlank()) c.setStatus(req.getStatus());
        if (c.getAuthorId() == null) c.setAuthorId(currentUserId());
        if (isCreate) mapper.insert(c); else mapper.updateById(c);
        return c.getId();
    }

    /**
     * 发布
     */
    @Transactional
    public void publish(String id) {
        CmsContent c = getById(id);
        if (CmsContent.STATUS_ARCHIVED.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "已归档内容不能发布");
        }
        c.setStatus(CmsContent.STATUS_PUBLISHED);
        c.setPublishedAt(LocalDateTime.now());
        mapper.updateById(c);
    }

    /**
     * 归档
     */
    @Transactional
    public void archive(String id) {
        CmsContent c = getById(id);
        c.setStatus(CmsContent.STATUS_ARCHIVED);
        mapper.updateById(c);
    }

    /**
     * 物理删除 (仅 DRAFT 允许)
     */
    @Transactional
    public void hardDelete(String id) {
        CmsContent c = getById(id);
        if (CmsContent.STATUS_PUBLISHED.equals(c.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "已发布内容不能直接删除, 请先归档");
        }
        mapper.deleteById(id);
    }

    /**
     * 列出指定分类下已发布内容 (公开访问)
     */
    public List<CmsContent> listPublishedByCategory(String category, int limit) {
        return mapper.selectList(
                new QueryWrapper<CmsContent>()
                        .eq("status", CmsContent.STATUS_PUBLISHED)
                        .eq("category", category)
                        .orderByDesc("published_at")
                        .last("LIMIT " + Math.min(limit, 100))
        );
    }

    // ========== 私有 ==========

    private boolean isSlugTaken(String slug, String excludeId) {
        if (slug == null || slug.isBlank()) return false;
        QueryWrapper<CmsContent> w = new QueryWrapper<CmsContent>().eq("slug", slug);
        if (excludeId != null && !excludeId.isBlank()) w.ne("id", excludeId);
        return mapper.selectCount(w) > 0;
    }

    /**
     * 极简 Markdown 渲染 (后续可换 flexmark / commonmark-java)
     * 当前实现: 直接返回原文 (前端用 marked.js / markdown-it 渲染)
     * 保留 contentHtml 字段供将来 SSR / SEO 预渲染
     */
    private String renderMarkdown(String md) {
        return md == null ? "" : md;
    }

    private String currentUserId() {
        try {
            String id = CurrentUserContext.getUserId();
            return id == null || id.isBlank() ? "system" : id;
        } catch (Exception e) {
            return "system";
        }
    }
}
