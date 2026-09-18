package com.yutong.system.tenant;

import com.yutong.system.tenant.domain.SysTenantPackage;
import com.yutong.system.tenant.dto.SaveTenantPackageRequest;
import com.yutong.system.tenant.mapper.SysTenantPackageMapper;
import com.yutong.system.tenant.service.TenantPackageService;
import com.yutong.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
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
 * 租户套餐服务单元测试
 * 设计来源: ADR 0004 P2-F 批次 6-B
 *
 * 覆盖:
 *  - save_create 首个 + 默认 DRAFT 状态
 *  - save_createDuplicateCode 抛错
 *  - save_updateSameCode 允许 (排除自己)
 *  - save_invalidCodeFormat 抛错 (regex)
 *  - publish 设 ACTIVE + publishedAt
 *  - publish_archived 抛错
 *  - archive 设 ARCHIVED
 *  - listActive 只返回 ACTIVE
 *  - getByCode_notFound 抛错
 */
class TenantPackageServiceTest {

    private SysTenantPackageMapper mapper;
    private TenantPackageService service;
    private final Map<String, SysTenantPackage> store = new HashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    @BeforeEach
    void setUp() {
        mapper = mock(SysTenantPackageMapper.class);
        service = new TenantPackageService(mapper);
        store.clear();
        nextId.set(1);

        when(mapper.selectById(any(String.class))).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(mapper.selectOne(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(inv -> {
            Object wObj = inv.getArgument(0);
            String sql = (String) wObj.getClass().getMethod("getSqlSegment").invoke(wObj);
            java.util.Map<?, ?> pnp = (java.util.Map<?, ?>) wObj.getClass().getMethod("getParamNameValuePairs").invoke(wObj);
            String targetCode = null;
            String excludeId = null;
            for (Object val : pnp.values()) {
                if (val instanceof String) {
                    String s = (String) val;
                    if (s.matches("^[A-Z][A-Z0-9_]{1,49}$")) {
                        if (targetCode == null) targetCode = s;
                    } else {
                        if (excludeId == null) excludeId = s;
                    }
                }
            }
            for (SysTenantPackage p : store.values()) {
                if (targetCode != null && !targetCode.equals(p.getPackageCode())) continue;
                if (excludeId != null && excludeId.equals(p.getId())) continue;
                return p;
            }
            return null;
        });
        when(mapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(inv -> {
            Object wObj = inv.getArgument(0);
            String targetCode = null;
            String excludeId = null;
            for (Object val : ((java.util.Map<?, ?>) safePnp(wObj)).values()) {
                if (val instanceof String) {
                    String s = (String) val;
                    if (s.matches("^[A-Z][A-Z0-9_]{1,49}$")) {
                        if (targetCode == null) targetCode = s;
                    } else {
                        if (excludeId == null) excludeId = s;
                    }
                }
            }
            long count = 0;
            for (SysTenantPackage p : store.values()) {
                if (targetCode != null && !targetCode.equals(p.getPackageCode())) continue;
                if (excludeId != null && excludeId.equals(p.getId())) continue;
                count++;
            }
            return count;
        });
        when(mapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(inv -> {
            String sql = (String) inv.getArgument(0).getClass().getMethod("getSqlSegment").invoke(inv.getArgument(0));
            java.util.Map<?, ?> pnp = (java.util.Map<?, ?>) inv.getArgument(0).getClass().getMethod("getParamNameValuePairs").invoke(inv.getArgument(0));
            String targetStatus = null;
            for (Object val : pnp.values()) {
                if (val instanceof String) {
                    String s = (String) val;
                    if ("DRAFT".equals(s) || "ACTIVE".equals(s) || "ARCHIVED".equals(s)) {
                        targetStatus = s;
                    }
                }
            }
            java.util.List<SysTenantPackage> result = new java.util.ArrayList<>();
            for (SysTenantPackage p : store.values()) {
                if (targetStatus != null && !targetStatus.equals(p.getStatus())) continue;
                result.add(p);
            }
            return result;
        });
        when(mapper.insert(any(SysTenantPackage.class))).thenAnswer(inv -> {
            SysTenantPackage p = inv.getArgument(0);
            if (p.getId() == null) p.setId("pkg-" + nextId.getAndIncrement());
            store.put(p.getId(), p);
            return 1;
        });
        when(mapper.updateById(any(SysTenantPackage.class))).thenAnswer(inv -> {
            SysTenantPackage p = inv.getArgument(0);
            store.put(p.getId(), p);
            return 1;
        });
    }

    private static Object safePnp(Object wObj) {
        try {
            return wObj.getClass().getMethod("getParamNameValuePairs").invoke(wObj);
        } catch (Exception e) {
            return java.util.Collections.emptyMap();
        }
    }

    private SaveTenantPackageRequest givenRequest(String code, String name) {
        SaveTenantPackageRequest req = new SaveTenantPackageRequest();
        req.setPackageCode(code);
        req.setPackageName(name);
        req.setDescription("test");
        req.setPriceCnyPerPeriod(new BigDecimal("99.00"));
        req.setPeriodMonths(1);
        req.setStatus(SysTenantPackage.STATUS_DRAFT);
        req.setSortNo(10);
        return req;
    }

    @Test
    void save_create_returnsId() {
        String id = service.save(givenRequest("BASIC", "基础版"));
        assertNotNull(id);
        assertEquals("基础版", store.get(id).getPackageName());
        assertEquals(SysTenantPackage.STATUS_DRAFT, store.get(id).getStatus());
    }

    @Test
    void save_create_callsInsertNotUpdate() {
        // 2026-09-07 回归: id 显式生成后 getId() 永非空, 创建必须走 insert (曾误判走 updateById 静默丢数据)
        service.save(givenRequest("BASIC", "基础版"));
        verify(mapper, times(1)).insert(any(SysTenantPackage.class));
        verify(mapper, never()).updateById(any(SysTenantPackage.class));
    }

    @Test
    void save_duplicateCode_throws() {
        service.save(givenRequest("BASIC", "基础版"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(givenRequest("BASIC", "另一个基础版")));
        assertTrue(ex.getMessage().contains("套餐编码已被占用"));
    }

    @Test
    void save_updateSameCodeAllowed() {
        String id = service.save(givenRequest("BASIC", "原名"));
        SaveTenantPackageRequest upd = givenRequest("BASIC", "新名");
        upd.setId(id);
        String newId = service.save(upd);
        assertEquals(id, newId);
        assertEquals("新名", store.get(id).getPackageName());
    }

    @Test
    void save_invalidCodeFormat_throws() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(givenRequest("basic-low", "low")));  // 小写开头 + 含连字符
        assertTrue(ex.getMessage().contains("套餐编码"));
    }

    @Test
    void publish_setsStatusAndTime() {
        String id = service.save(givenRequest("BASIC", "基础版"));
        service.publish(id);
        SysTenantPackage p = store.get(id);
        assertEquals(SysTenantPackage.STATUS_ACTIVE, p.getStatus());
        assertNotNull(p.getPublishedAt());
    }

    @Test
    void publish_archived_throws() {
        String id = service.save(givenRequest("BASIC", "基础版"));
        service.archive(id);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.publish(id));
        assertTrue(ex.getMessage().contains("归档"));
    }

    @Test
    void archive_setsStatus() {
        String id = service.save(givenRequest("BASIC", "基础版"));
        service.archive(id);
        assertEquals(SysTenantPackage.STATUS_ARCHIVED, store.get(id).getStatus());
    }

    @Test
    void listActive_returnsOnlyActive() {
        service.save(givenRequest("DRAFT", "草稿"));
        String id2 = service.save(givenRequest("ACTIVE", "已发布"));
        service.publish(id2);
        service.save(givenRequest("ARCH", "已归档"));
        java.util.List<SysTenantPackage> active = service.listActive();
        assertEquals(1, active.size());
        assertEquals("已发布", active.get(0).getPackageName());
    }

    @Test
    void getByCode_notFound_throws() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.getByCode("nonexist"));
        assertTrue(ex.getMessage().contains("套餐不存在"));
    }
}
