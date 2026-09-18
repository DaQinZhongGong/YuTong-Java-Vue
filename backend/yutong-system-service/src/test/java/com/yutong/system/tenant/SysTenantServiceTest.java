package com.yutong.system.tenant;

import com.yutong.common.exception.BusinessException;
import com.yutong.system.tenant.domain.SysTenant;
import com.yutong.system.tenant.domain.SysTenantPackage;
import com.yutong.system.tenant.dto.SaveTenantRequest;
import com.yutong.system.tenant.mapper.SysTenantMapper;
import com.yutong.system.tenant.service.SysTenantService;
import com.yutong.system.tenant.service.TenantPackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户注册表服务单元测试。
 * 设计来源: 业界同类实现 SysTenant + ADR 0004 P2-F。
 *
 * 覆盖:
 *  - save_create 首个 + 默认 NORMAL + tenantId 自描述 + 走 insert (2026-09-07 insert 死代码回归)
 *  - save_update 走 updateById
 *  - save_duplicateCode 抛错
 *  - save_changeCode 抛错 (编码不可改)
 *  - save_illegalAccountCount 抛错
 *  - disable/enable 状态流转
 *  - disable_default 抛错 (内置保护)
 *  - delete 仅 DISABLED, default 禁止删除
 *  - assignPackage 归档套餐拒绝
 */
class SysTenantServiceTest {

    private SysTenantMapper mapper;
    private TenantPackageService packageService;
    private SysTenantService service;
    private final Map<String, SysTenant> store = new HashMap<>();
    private final Map<String, SysTenantPackage> packages = new HashMap<>();

    @BeforeEach
    void setUp() {
        mapper = mock(SysTenantMapper.class);
        packageService = mock(TenantPackageService.class);
        service = new SysTenantService(mapper, packageService);
        store.clear();
        packages.clear();

        SysTenantPackage active = new SysTenantPackage();
        active.setId("pkg-active");
        active.setPackageCode("PRO");
        active.setStatus(SysTenantPackage.STATUS_ACTIVE);
        packages.put("pkg-active", active);
        SysTenantPackage archived = new SysTenantPackage();
        archived.setId("pkg-archived");
        archived.setPackageCode("OLD");
        archived.setStatus(SysTenantPackage.STATUS_ARCHIVED);
        packages.put("pkg-archived", archived);

        when(packageService.getById(any(String.class))).thenAnswer(inv -> {
            SysTenantPackage p = packages.get(inv.getArgument(0));
            if (p == null) throw new BusinessException(
                    com.yutong.common.errorcode.ErrorCode.SYS_PARAM_INVALID, "套餐不存在");
            return p;
        });
        when(mapper.selectById(any(String.class))).thenAnswer(inv -> store.get(inv.getArgument(0)));
        when(mapper.selectCount(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class))).thenAnswer(inv -> {
            Object wObj = inv.getArgument(0);
            java.util.Map<?, ?> pnp;
            try {
                pnp = (java.util.Map<?, ?>) wObj.getClass().getMethod("getParamNameValuePairs").invoke(wObj);
            } catch (Exception e) {
                pnp = java.util.Collections.emptyMap();
            }
            String targetCode = null;
            for (Object val : pnp.values()) {
                if (val instanceof String s && s.matches("^[a-z0-9][a-z0-9-]{1,31}$")) {
                    if (targetCode == null) targetCode = s;
                }
            }
            long count = 0;
            for (SysTenant t : store.values()) {
                if (targetCode != null && !targetCode.equals(t.getTenantCode())) continue;
                count++;
            }
            return count;
        });
        when(mapper.insert(any(SysTenant.class))).thenAnswer(inv -> {
            SysTenant t = inv.getArgument(0);
            store.put(t.getId(), t);
            return 1;
        });
        when(mapper.updateById(any(SysTenant.class))).thenAnswer(inv -> {
            SysTenant t = inv.getArgument(0);
            store.put(t.getId(), t);
            return 1;
        });
    }

    private SaveTenantRequest givenRequest(String code, String company) {
        SaveTenantRequest req = new SaveTenantRequest();
        req.setTenantCode(code);
        req.setCompanyName(company);
        req.setContactUserName("张三");
        req.setAccountCount(100L);
        return req;
    }

    @Test
    void save_create_returnsIdWithNormalAndSelfTenant() {
        String id = service.save(givenRequest("acme", "Acme 公司"));

        assertNotNull(id);
        SysTenant saved = store.get(id);
        assertEquals(SysTenant.STATUS_NORMAL, saved.getStatus());
        assertEquals("acme", saved.getTenantId());
        // 2026-09-07 回归: 创建必须走 insert (TenantPackage/CMS 曾因 getId() 非空误判走 updateById 静默丢数据)
        verify(mapper, times(1)).insert(any(SysTenant.class));
        verify(mapper, never()).updateById(any(SysTenant.class));
    }

    @Test
    void save_update_usesUpdateById() {
        String id = service.save(givenRequest("acme", "Acme"));
        SaveTenantRequest upd = givenRequest("acme", "Acme 新名");
        upd.setId(id);
        service.save(upd);

        assertEquals("Acme 新名", store.get(id).getCompanyName());
        verify(mapper, times(1)).insert(any(SysTenant.class));
        verify(mapper, times(1)).updateById(any(SysTenant.class));
    }

    @Test
    void save_duplicateCode_throws() {
        service.save(givenRequest("acme", "Acme"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.save(givenRequest("acme", "另一家")));
        assertTrue(ex.getMessage().contains("租户编码已被占用"));
    }

    @Test
    void save_changeCode_throws() {
        String id = service.save(givenRequest("acme", "Acme"));
        SaveTenantRequest upd = givenRequest("other", "Acme");
        upd.setId(id);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(upd));
        assertTrue(ex.getMessage().contains("不可修改"));
    }

    @Test
    void save_illegalAccountCount_throws() {
        SaveTenantRequest req = givenRequest("acme", "Acme");
        req.setAccountCount(-2L);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.save(req));
        assertTrue(ex.getMessage().contains("账号上限非法"));
    }

    @Test
    void disable_enable_flows() {
        String id = service.save(givenRequest("acme", "Acme"));
        service.disable(id);
        assertEquals(SysTenant.STATUS_DISABLED, store.get(id).getStatus());
        service.enable(id);
        assertEquals(SysTenant.STATUS_NORMAL, store.get(id).getStatus());
    }

    @Test
    void disable_default_throws() {
        String id = service.save(givenRequest("default", "默认租户"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.disable(id));
        assertTrue(ex.getMessage().contains("禁止停用"));
    }

    @Test
    void delete_requiresDisabled() {
        String id = service.save(givenRequest("acme", "Acme"));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(id));
        assertTrue(ex.getMessage().contains("先停用"));
        service.disable(id);
        service.delete(id);
    }

    @Test
    void delete_default_throws() {
        String id = service.save(givenRequest("default", "默认租户"));
        // default 即便停用也不可删: 先绕过 disable 保护验证 delete 的内置保护
        store.get(id).setStatus(SysTenant.STATUS_DISABLED);
        BusinessException ex = assertThrows(BusinessException.class, () -> service.delete(id));
        assertTrue(ex.getMessage().contains("禁止删除"));
    }

    @Test
    void assignPackage_archived_throws() {
        String id = service.save(givenRequest("acme", "Acme"));
        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.assignPackage(id, "pkg-archived", null));
        assertTrue(ex.getMessage().contains("已归档"));
    }

    @Test
    void assignPackage_active_setsPackage() {
        String id = service.save(givenRequest("acme", "Acme"));
        service.assignPackage(id, "pkg-active", null);
        assertEquals("pkg-active", store.get(id).getPackageId());
    }
}
