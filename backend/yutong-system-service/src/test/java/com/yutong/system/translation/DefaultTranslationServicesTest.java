package com.yutong.system.translation;

import com.yutong.common.translation.TranslationCache;
import com.yutong.common.translation.TranslationRegistry;
import com.yutong.common.translation.TranslationType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 默认 TranslationService 边界测试
 * 设计来源: ADR 0004 P2-A 批次 2-B
 *
 * 覆盖:
 *  - null/blank id 返回 null (不抛错)
 *  - null/blank dictType (Dict) 返回 null
 *  - JdbcTemplate 抛错时静默返回 null
 *  - TranslationConfig 正确注册 5 个 service
 */
class DefaultTranslationServicesTest {

    @Test
    void dictService_nullId_returnsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DefaultDictTranslationService svc = new DefaultDictTranslationService(jdbc);
        assertNull(svc.translate(null, "sys_user_status"));
        assertNull(svc.translate("", "sys_user_status"));
    }

    @Test
    void dictService_nullDictType_returnsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DefaultDictTranslationService svc = new DefaultDictTranslationService(jdbc);
        assertNull(svc.translate("ENABLED", null));
        assertNull(svc.translate("ENABLED", ""));
    }

    @Test
    void dictService_jdbcError_silentlyReturnsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class)))
                .thenThrow(new RuntimeException("DB down"));
        DefaultDictTranslationService svc = new DefaultDictTranslationService(jdbc);
        assertNull(svc.translate("ENABLED", "sys_user_status"));
    }

    @Test
    void dictService_hitRow_returnsLabel() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        java.util.List<java.util.Map<String, Object>> rows = java.util.List.of(
                java.util.Map.of("item_label", "启用"));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(rows);
        DefaultDictTranslationService svc = new DefaultDictTranslationService(jdbc);
        assertEquals("启用", svc.translate("ENABLED", "sys_user_status"));
    }

    @Test
    void userService_nullId_returnsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DefaultUserTranslationService svc = new DefaultUserTranslationService(jdbc);
        assertNull(svc.translate(null, null));
    }

    @Test
    void userService_prefersDisplayNameOverUsername() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        java.util.List<java.util.Map<String, Object>> rows = java.util.List.of(
                java.util.Map.of("username", "admin", "display_name", "系统管理员"));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(rows);
        DefaultUserTranslationService svc = new DefaultUserTranslationService(jdbc);
        assertEquals("系统管理员", svc.translate("u100", null), "display_name 优先");
    }

    @Test
    void userService_fallbackToUsernameWhenDisplayNameBlank() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        java.util.List<java.util.Map<String, Object>> rows = java.util.List.of(
                java.util.Map.of("username", "admin", "display_name", ""));
        when(jdbc.queryForList(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(rows);
        DefaultUserTranslationService svc = new DefaultUserTranslationService(jdbc);
        assertEquals("admin", svc.translate("u100", null), "display_name 空时回退 username");
    }

    @Test
    void deptService_nullId_returnsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DefaultDeptTranslationService svc = new DefaultDeptTranslationService(jdbc);
        assertNull(svc.translate(null, null));
    }

    @Test
    void postService_nullId_returnsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DefaultPostTranslationService svc = new DefaultPostTranslationService(jdbc);
        assertNull(svc.translate("", null));
    }

    @Test
    void roleService_nullId_returnsNull() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DefaultRoleTranslationService svc = new DefaultRoleTranslationService(jdbc);
        assertNull(svc.translate(null, null));
    }

    @Test
    void translationConfig_registersAllFiveTypes() {
        TranslationConfig config = new TranslationConfig();
        TranslationCache cache = config.translationCache();
        assertNotNull(cache);
        assertEquals(0, cache.size());

        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        TranslationRegistry reg = config.translationRegistry(
                new DefaultDictTranslationService(jdbc),
                new DefaultUserTranslationService(jdbc),
                new DefaultDeptTranslationService(jdbc),
                new DefaultPostTranslationService(jdbc),
                new DefaultRoleTranslationService(jdbc));

        // 5 个 type 全部注册
        var types = reg.registeredTypes();
        assertEquals(5, types.size());
        org.junit.jupiter.api.Assertions.assertTrue(types.contains(TranslationType.DICT));
        org.junit.jupiter.api.Assertions.assertTrue(types.contains(TranslationType.USER));
        org.junit.jupiter.api.Assertions.assertTrue(types.contains(TranslationType.DEPT));
        org.junit.jupiter.api.Assertions.assertTrue(types.contains(TranslationType.POST));
        org.junit.jupiter.api.Assertions.assertTrue(types.contains(TranslationType.ROLE));
    }

    @Test
    void translationConfig_doesNotThrowOnNullArgs() {
        TranslationConfig config = new TranslationConfig();
        assertDoesNotThrow(() -> {
            TranslationRegistry reg = config.translationRegistry(null, null, null, null, null);
            // 即使 5 个 service 全 null, 仍能创建 registry, 后续 register 是 no-op
            reg.register(TranslationType.DICT, null);
            assertEquals(0, reg.registeredTypes().size());
        });
    }
}
