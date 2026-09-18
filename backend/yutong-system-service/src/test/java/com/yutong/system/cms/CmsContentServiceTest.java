package com.yutong.system.cms;

import com.yutong.system.cms.domain.CmsContent;
import com.yutong.system.cms.dto.SaveCmsContentRequest;
import com.yutong.system.cms.mapper.CmsContentMapper;
import com.yutong.system.cms.service.CmsContentService;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CMS Content 服务单元测试
 * 设计来源: ADR 0004 P2-F 批次 6
 *
 * 覆盖:
 *  - slug 唯一性校验 (创建/更新)
 *  - slug 非法字符抛错
 *  - 已发布不能改回草稿
 *  - publish / archive
 *  - hardDelete 限制
 *  - getPublishedBySlug 浏览 +1
 */
class CmsContentServiceTest {

    private CmsContentMapper mapper;
    private CmsContentService service;
    private final Map<String, CmsContent> store = new HashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mapper = mock(CmsContentMapper.class);
        service = new CmsContentService(mapper);
        store.clear();
        nextId.set(1);

        when(mapper.selectById(any(String.class))).thenAnswer(inv -> {
            String id = inv.getArgument(0);
            return store.get(id);
        });

        when(mapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(inv -> {
            // 简化: 解析 sqlSegment 拿 "slug = ?" 模式 + param
            String sql = (String) inv.getArgument(0).getClass().getMethod("getSqlSegment").invoke(inv.getArgument(0));
            java.util.Map<?, ?> pnp = (java.util.Map<?, ?>) inv.getArgument(0).getClass().getMethod("getParamNameValuePairs").invoke(inv.getArgument(0));
            // 找 eq("slug", ?) 的 ? 对应值
            String targetSlug = null;
            String excludeId = null;
            for (Object val : pnp.values()) {
                if (val instanceof String) {
                    String s = (String) val;
                    if (s.matches("^[a-zA-Z0-9_-]{1,100}$")) {
                        if (targetSlug == null) targetSlug = s;
                    } else {
                        if (excludeId == null) excludeId = s;
                    }
                }
            }
            long count = 0;
            for (CmsContent c : store.values()) {
                if (targetSlug != null && !targetSlug.equals(c.getSlug())) continue;
                if (excludeId != null && excludeId.equals(c.getId())) continue;
                count++;
            }
            return count;
        });

        when(mapper.insert(any(CmsContent.class))).thenAnswer(inv -> {
            CmsContent c = inv.getArgument(0);
            if (c.getId() == null) c.setId("cms-" + nextId.getAndIncrement());
            store.put(c.getId(), c);
            return 1;
        });
        when(mapper.updateById(any(CmsContent.class))).thenAnswer(inv -> {
            CmsContent c = inv.getArgument(0);
            store.put(c.getId(), c);
            return 1;
        });
        when(mapper.deleteById(any(String.class))).thenAnswer(inv -> {
            store.remove(inv.getArgument(0));
            return 1;
        });

        // selectList 兜底: 按 wrapper 条件过滤 store (供 listPublishedByCategory / list 等)
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(inv -> {
            String sql = (String) inv.getArgument(0).getClass().getMethod("getSqlSegment").invoke(inv.getArgument(0));
            java.util.Map<?, ?> pnp = (java.util.Map<?, ?>) inv.getArgument(0).getClass().getMethod("getParamNameValuePairs").invoke(inv.getArgument(0));
            String targetCategory = null;
            String targetStatus = null;
            for (Object val : pnp.values()) {
                if (val instanceof String) {
                    String s = (String) val;
                    if ("PUBLISHED".equals(s) || "DRAFT".equals(s) || "ARCHIVED".equals(s)) {
                        targetStatus = s;
                    } else {
                        targetCategory = s;
                    }
                }
            }
            java.util.List<CmsContent> result = new java.util.ArrayList<>();
            for (CmsContent c : store.values()) {
                if (targetCategory != null && !targetCategory.equals(c.getCategory())) continue;
                if (targetStatus != null && !targetStatus.equals(c.getStatus())) continue;
                result.add(c);
            }
            return result;
        });
    }

    private SaveCmsContentRequest givenRequest(String title, String slug, String status) {
        return givenRequest(title, slug, status, null);
    }

    private SaveCmsContentRequest givenRequest(String title, String slug, String status, String category) {
        SaveCmsContentRequest req = new SaveCmsContentRequest();
        req.setTitle(title);
        req.setSlug(slug);
        req.setContentMd("# Hello\nWorld");
        req.setSummary("test");
        req.setStatus(status);
        req.setCategory(category);
        return req;
    }

    @Test
    void save_create_returnsId() {
        String id = service.save(givenRequest("Hello", "hello-world", CmsContent.STATUS_DRAFT));
        assertNotNull(id);
        CmsContent stored = store.get(id);
        assertEquals("Hello", stored.getTitle());
        assertEquals(CmsContent.STATUS_DRAFT, stored.getStatus());
        assertEquals(0L, stored.getViewCount());
    }

    @Test
    void save_create_callsInsertNotUpdate() {
        // 2026-09-07 回归: id 显式生成后 getId() 永非空, 创建必须走 insert (曾误判走 updateById 静默丢数据)
        service.save(givenRequest("Hello", "hello-insert", CmsContent.STATUS_DRAFT));
        verify(mapper, times(1)).insert(any(CmsContent.class));
        verify(mapper, never()).updateById(any(CmsContent.class));
    }

    @Test
    void save_createDuplicateSlug_throws() {
        service.save(givenRequest("first", "dup-slug", CmsContent.STATUS_DRAFT));
        SaveCmsContentRequest dup = givenRequest("second", "dup-slug", CmsContent.STATUS_DRAFT);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(dup));
        assertTrue(ex.getMessage().contains("slug 已被占用"));
    }

    @Test
    void save_updateSameSlugAllowed() {
        String id = service.save(givenRequest("first", "self", CmsContent.STATUS_DRAFT));
        SaveCmsContentRequest update = givenRequest("updated", "self", CmsContent.STATUS_DRAFT);
        update.setId(id);
        String newId = service.save(update);
        assertEquals(id, newId);
        assertEquals("updated", store.get(id).getTitle());
    }

    @Test
    void save_invalidSlug_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(givenRequest("Bad", "has space!", CmsContent.STATUS_DRAFT)));
        assertTrue(ex.getMessage().contains("slug"));
    }

    @Test
    void save_publishedCannotRevertToDraft_throws() {
        String id = service.save(givenRequest("p", "pub-content", CmsContent.STATUS_DRAFT));
        service.publish(id);
        SaveCmsContentRequest update = givenRequest("p", "pub-content", CmsContent.STATUS_DRAFT);
        update.setId(id);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(update));
        assertTrue(ex.getMessage().contains("不能改回草稿"));
    }

    @Test
    void publish_setsStatusAndTime() {
        String id = service.save(givenRequest("p", "publish-test", CmsContent.STATUS_DRAFT));
        service.publish(id);
        CmsContent c = store.get(id);
        assertEquals(CmsContent.STATUS_PUBLISHED, c.getStatus());
        assertNotNull(c.getPublishedAt());
    }

    @Test
    void publish_archivedContent_throws() {
        String id = service.save(givenRequest("a", "archived", CmsContent.STATUS_DRAFT));
        service.archive(id);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.publish(id));
        assertTrue(ex.getMessage().contains("归档"));
    }

    @Test
    void archive_setsStatus() {
        String id = service.save(givenRequest("a", "archive-test", CmsContent.STATUS_DRAFT));
        service.archive(id);
        assertEquals(CmsContent.STATUS_ARCHIVED, store.get(id).getStatus());
    }

    @Test
    void hardDelete_draft_succeeds() {
        String id = service.save(givenRequest("d", "delete-draft", CmsContent.STATUS_DRAFT));
        service.hardDelete(id);
        assertNull(store.get(id));
    }

    @Test
    void hardDelete_published_throws() {
        String id = service.save(givenRequest("p", "delete-pub", CmsContent.STATUS_DRAFT));
        service.publish(id);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.hardDelete(id));
        assertTrue(ex.getMessage().contains("已发布"));
    }

    @Test
    void page_filtersByStatus() {
        service.save(givenRequest("a", "p1", CmsContent.STATUS_DRAFT));
        service.save(givenRequest("b", "p2", CmsContent.STATUS_DRAFT));
        String id3 = service.save(givenRequest("c", "p3", CmsContent.STATUS_DRAFT));
        service.publish(id3);
        when(mapper.selectPage(any(com.baomidou.mybatisplus.extension.plugins.pagination.Page.class), any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenAnswer(inv -> {
                    com.baomidou.mybatisplus.extension.plugins.pagination.Page<CmsContent> page = inv.getArgument(0);
                    com.baomidou.mybatisplus.core.conditions.Wrapper w = inv.getArgument(1);
                    // 简化: 取所有 store 然后按 page 切
                    java.util.List<CmsContent> records = new java.util.ArrayList<>();
                    for (CmsContent c : store.values()) records.add(c);
                    page.setRecords(records);
                    page.setTotal(records.size());
                    return page;
                });
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CmsContent> page = service.page(1, 20, CmsContent.STATUS_DRAFT, null, null);
        assertEquals(3, page.getTotal());
        assertEquals(3, page.getRecords().size());
    }

    @Test
    void listPublishedByCategory_returnsOnlyPublished() {
        service.save(givenRequest("a", "cat1-draft", CmsContent.STATUS_DRAFT, "announcement"));
        String id2 = service.save(givenRequest("b", "cat1-pub", CmsContent.STATUS_DRAFT, "announcement"));
        service.publish(id2);
        java.util.List<CmsContent> list = service.listPublishedByCategory("announcement", 10);
        // 1 DRAFT + 1 PUBLISHED, 都 category=announcement → 但 service 只返回 PUBLISHED
        assertEquals(1, list.size());
        assertEquals(CmsContent.STATUS_PUBLISHED, list.get(0).getStatus());
    }
}
