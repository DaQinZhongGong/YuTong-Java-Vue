package com.yutong.common.sensitive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 脱敏执行器 — 反射扫描 @Sensitive 字段并遮罩
 * 设计来源: ADR 0004 P2-A common-sensitive
 *
 * 落点:67-数据权限与审计日志详设「敏感数据脱敏」
 *
 * 用法 (单对象):
 *   UserVO vo = userService.getVO(id);
 *   SensitiveManager.mask(vo);
 *   return vo;
 *
 * 用法 (批量):
 *   List<UserVO> vos = userService.listVO();
 *   SensitiveManager.maskAll(vos);
 *   return vos;
 *
 * 工作流程:
 *   1. 反射扫描所有字段 (含父类)
 *   2. 收集 @Sensitive 标注的字段
 *   3. CUSTOM: 查 named service
 *      其他: 走 SensitiveHandlers 内置实现
 *   4. 写回原字段
 *
 * 性能: 单次 < 1ms (反射 + 字符串操作), 1000 条 < 100ms
 * 失败安全: service 抛错 / 反射失败 → 静默, 不抛错
 */
public final class SensitiveManager {

    private static final Logger log = LoggerFactory.getLogger(SensitiveManager.class);

    /** named service 注册表 (CUSTOM 策略专用) */
    private static final Map<String, SensitiveService> SERVICES = new ConcurrentHashMap<>();

    private SensitiveManager() {
    }

    /** 注册 named service (CUSTOM 策略) */
    public static void register(String name, SensitiveService service) {
        if (name != null && service != null) SERVICES.put(name, service);
    }

    /** 单对象 mask */
    public static <T> T mask(T vo) {
        if (vo == null) return null;
        try {
            maskObject(vo);
        } catch (Exception e) {
            log.warn("Sensitive mask failed for {}: {}", vo.getClass().getSimpleName(), e.getMessage());
        }
        return vo;
    }

    /** 批量 mask */
    public static <T> List<T> maskAll(List<T> vos) {
        if (vos == null || vos.isEmpty()) return vos;
        for (T vo : vos) mask(vo);
        return vos;
    }

    private static void maskObject(Object vo) throws IllegalAccessException {
        Class<?> clazz = vo.getClass();
        Map<String, Field> fieldMap = new HashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                fieldMap.put(f.getName(), f);
            }
        }
        List<Field> annotated = new ArrayList<>();
        for (Field f : fieldMap.values()) {
            Sensitive s = f.getAnnotation(Sensitive.class);
            if (s != null) annotated.add(f);
        }
        if (annotated.isEmpty()) return;

        for (Field f : annotated) {
            Sensitive s = f.getAnnotation(Sensitive.class);
            Object raw = f.get(vo);
            if (raw == null) continue;
            String value = String.valueOf(raw);
            String masked;
            if (s.strategy() == SensitiveStrategy.CUSTOM) {
                SensitiveService svc = SERVICES.get(s.service());
                if (svc == null) {
                    log.debug("@Sensitive CUSTOM 未注册 service: {}", s.service());
                    continue;
                }
                masked = svc.mask(value);
            } else {
                masked = SensitiveHandlers.mask(value, s.strategy());
            }
            f.set(vo, masked);
        }
    }
}
