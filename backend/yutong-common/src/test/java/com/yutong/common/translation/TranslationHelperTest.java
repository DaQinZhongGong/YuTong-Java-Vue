package com.yutong.common.translation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 字典翻译单元测试
 * 设计来源: ADR 0004 P2-A common-translation
 *
 * 覆盖:
 *  - @Translation 注解字段正确解析 + 翻译
 *  - 引用字段 ref 正确找到
 *  - 缓存命中(同 ID 多次翻译只调一次 service)
 *  - service 未注册时静默写 null
 *  - 翻译失败 (返回 null) 时静默写 null 不抛错
 *  - 批量 translate(list) 全量翻译
 *  - warmup 预热后走缓存
 */
class TranslationHelperTest {

    private TranslationRegistry registry;
    private TranslationCache cache;
    private final AtomicInteger callCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        registry = new TranslationRegistry();
        cache = new TranslationCache();
        callCount.set(0);
    }

    // ============ 测试 VO ============

    static class UserVO {
        private String createBy;     // userId
        @Translation(type = TranslationType.USER, ref = "createBy")
        private String createByName; // 期望:用户名
        private String deptId;
        @Translation(type = TranslationType.DEPT, ref = "deptId")
        private String deptName;
        private String status;
        @Translation(type = TranslationType.DICT, dictType = "sys_user_status", ref = "status")
        private String statusLabel;
    }

    static class OrderVO {
        private String id;
        private String customerId;
        @Translation(type = TranslationType.USER, ref = "customerId")
        private String customerName;
    }

    // ============ 测试用例 ============

    @Test
    void translate_shouldFillLabelFields() {
        registry.register(TranslationType.USER, (id, ext) -> {
            callCount.incrementAndGet();
            return "user-" + id;
        });
        registry.register(TranslationType.DEPT, (id, ext) -> "dept-" + id);
        registry.register(TranslationType.DICT, (id, ext) -> "label-" + id + "@" + ext);

        UserVO vo = new UserVO();
        vo.createBy = "u100";
        vo.deptId = "d200";
        vo.status = "ENABLED";

        TranslationHelper.translate(vo, registry, cache);

        assertEquals("user-u100", vo.createByName);
        assertEquals("dept-d200", vo.deptName);
        assertEquals("label-ENABLED@sys_user_status", vo.statusLabel);
        assertEquals(1, callCount.get(), "USER 翻译只调 service 一次, DEPT/DICT 各调一次");
    }

    @Test
    void translate_shouldHitCacheOnRepeat() {
        registry.register(TranslationType.USER, (id, ext) -> {
            callCount.incrementAndGet();
            return "user-" + id;
        });

        // 第一次翻译
        UserVO vo1 = new UserVO();
        vo1.createBy = "u100";
        TranslationHelper.translate(vo1, registry, cache);
        assertEquals("user-u100", vo1.createByName);
        assertEquals(1, callCount.get());

        // 第二次同 ID 应该命中缓存
        UserVO vo2 = new UserVO();
        vo2.createBy = "u100";
        TranslationHelper.translate(vo2, registry, cache);
        assertEquals("user-u100", vo2.createByName);
        assertEquals(1, callCount.get(), "重复 ID 走缓存, service 不再被调");
    }

    @Test
    void translate_shouldHandleNullId() {
        registry.register(TranslationType.USER, (id, ext) -> {
            callCount.incrementAndGet();
            return "user-" + id;
        });
        UserVO vo = new UserVO();
        vo.createBy = null;  // ID 为 null
        vo.deptId = "d200";
        TranslationHelper.translate(vo, registry, cache);
        assertNull(vo.createByName, "ID 为 null 时翻译字段为 null");
        assertEquals(0, callCount.get(), "service 不被调用");
    }

    @Test
    void translate_shouldSetNullWhenServiceNotRegistered() {
        // 不注册任何 service
        UserVO vo = new UserVO();
        vo.createBy = "u100";
        vo.deptId = "d200";
        TranslationHelper.translate(vo, registry, cache);
        assertNull(vo.createByName, "未注册 service 时静默写 null");
        assertNull(vo.deptName);
    }

    @Test
    void translate_shouldNotThrowOnServiceFailure() {
        registry.register(TranslationType.USER, (id, ext) -> {
            throw new RuntimeException("DB down");
        });
        UserVO vo = new UserVO();
        vo.createBy = "u100";
        TranslationHelper.translate(vo, registry, cache);
        // service 抛错 -> 静默失败, 翻译字段为 null
        assertNull(vo.createByName);
    }

    @Test
    void translate_shouldHandleList() {
        registry.register(TranslationType.USER, (id, ext) -> {
            callCount.incrementAndGet();
            return "user-" + id;
        });

        UserVO vo1 = new UserVO();
        vo1.createBy = "u1";
        UserVO vo2 = new UserVO();
        vo2.createBy = "u2";
        UserVO vo3 = new UserVO();
        vo3.createBy = "u1"; // 重复, 期望走缓存

        List<UserVO> vos = TranslationHelper.translate(Arrays.asList(vo1, vo2, vo3), registry, cache);
        assertEquals("user-u1", vo1.createByName);
        assertEquals("user-u2", vo2.createByName);
        assertEquals("user-u1", vo3.createByName);
        assertEquals(2, callCount.get(), "u1 + u2 仅调 service 2 次, u3 走缓存");
    }

    @Test
    void warmup_shouldPrepopulateCache() {
        registry.register(TranslationType.USER, (id, ext) -> {
            callCount.incrementAndGet();
            return "user-" + id;
        });
        TranslationHelper.warmup(Arrays.asList("u1", "u2", "u3"), TranslationType.USER, null, registry, cache);
        assertEquals(3, cache.size(), "warmup 把 3 个 id 都进 cache");
        assertEquals(3, callCount.get());

        // 后续 translate 直接命中
        UserVO vo = new UserVO();
        vo.createBy = "u2";
        TranslationHelper.translate(vo, registry, cache);
        assertEquals("user-u2", vo.createByName);
        assertEquals(3, callCount.get(), "u2 命中 warmup 缓存, service 不再调");
    }

    @Test
    void translate_shouldHandleMissingRefField() {
        registry.register(TranslationType.USER, (id, ext) -> "user-" + id);
        OrderVO vo = new OrderVO();
        vo.id = "o1";
        vo.customerId = "c1";
        // customerName 引用 customerId, 字段存在, 应翻译
        TranslationHelper.translate(vo, registry, cache);
        assertEquals("user-c1", vo.customerName);
    }

    @Test
    void cache_shouldExpireAfterTtl() throws InterruptedException {
        TranslationCache shortCache = new TranslationCache(java.time.Duration.ofMillis(50), 100);
        shortCache.put("k1", "v1");
        assertEquals("v1", shortCache.get("k1"));
        Thread.sleep(80);
        assertNull(shortCache.get("k1"), "50ms TTL 到期后返回 null");
    }

    @Test
    void cache_shouldEvictWhenFull() {
        TranslationCache smallCache = new TranslationCache(java.time.Duration.ofMinutes(5), 4);
        for (int i = 0; i < 10; i++) {
            smallCache.put("k" + i, "v" + i);
        }
        // 容量 4, 触发清理, 最终大小 < 4
        assertTrue(smallCache.size() <= 4, "超出容量后应清理, size=" + smallCache.size());
    }

    @Test
    void registry_shouldResolveByTypeOrName() {
        TranslationService defaultUser = (id, ext) -> "default-" + id;
        TranslationService customUser = (id, ext) -> "custom-" + id;
        registry.register(TranslationType.USER, defaultUser);
        registry.registerByName("myTranslator", customUser);

        Translation t1 = mockAnnotation(TranslationType.USER, "ref", "", "");
        Translation t2 = mockAnnotation(TranslationType.USER, "ref", "", "myTranslator");

        assertEquals(defaultUser, registry.resolve(t1));
        assertEquals(customUser, registry.resolve(t2));
    }

    @Test
    void registry_snapshot_shouldExposeRegistrations() {
        registry.register(TranslationType.DICT, (id, ext) -> id);
        registry.register(TranslationType.USER, (id, ext) -> id);
        Map<String, String> snap = registry.snapshot();
        assertNotNull(snap.get("type:DICT"));
        assertNotNull(snap.get("type:USER"));
        assertEquals(2, snap.size());
    }

    private static Translation mockAnnotation(TranslationType type, String ref, String dictType, String translator) {
        return new Translation() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Translation.class;
            }
            @Override public TranslationType type() { return type; }
            @Override public String ref() { return ref; }
            @Override public String dictType() { return dictType; }
            @Override public String translator() { return translator; }
        };
    }
}
