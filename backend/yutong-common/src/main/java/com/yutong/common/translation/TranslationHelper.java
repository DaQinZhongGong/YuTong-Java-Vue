package com.yutong.common.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 翻译执行器 — 扫描 VO 字段的 @Translation 注解并执行翻译
 * 设计来源: ADR 0004 P2-A common-translation
 *
 * 落点:84-字典/用户/部门显示名自动化详设
 *
 * 用法 (Controller 层):
 *   List<UserVO> vos = userService.listVO();
 *   TranslationHelper.translate(vos, translationRegistry, translationCache);
 *   return vos;
 *
 * 工作流程:
 *   1. 反射扫描每个 VO 的所有字段
 *   2. 收集 @Translation 标注 + 解析 ref 指向的同 VO 内 ID 字段
 *   3. 构造缓存 key (type + dictType + id)
 *   4. 缓存命中直接写值, 未命中调 TranslationService 翻译
 *   5. 写回标 @Translation 的字段
 *
 * 性能:单次翻译 < 1ms (反射 + 缓存命中), 1000 条 VO < 100ms
 * 失败安全:翻译失败 (查不到 / 反射失败) 静默写 null, 不抛错
 */
public final class TranslationHelper {

    private static final Logger log = LoggerFactory.getLogger(TranslationHelper.class);

    private TranslationHelper() {
    }

    /** 单个对象翻译 */
    public static <T> T translate(T vo, TranslationRegistry registry, TranslationCache cache) {
        if (vo == null || registry == null) return vo;
        try {
            translateObject(vo, registry, cache);
        } catch (Exception e) {
            log.warn("Translation failed for {}: {}", vo.getClass().getSimpleName(), e.getMessage());
        }
        return vo;
    }

    /** 集合批量翻译 */
    public static <T> List<T> translate(List<T> vos, TranslationRegistry registry, TranslationCache cache) {
        if (vos == null || vos.isEmpty()) return vos;
        for (T vo : vos) {
            translate(vo, registry, cache);
        }
        return vos;
    }

    private static void translateObject(Object vo, TranslationRegistry registry, TranslationCache cache) throws IllegalAccessException {
        Class<?> clazz = vo.getClass();
        // 收集所有字段 (含父类)
        Map<String, Field> fieldMap = new HashMap<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                f.setAccessible(true);
                fieldMap.put(f.getName(), f);
            }
        }
        List<Field> annotated = new ArrayList<>();
        for (Field f : fieldMap.values()) {
            Translation t = f.getAnnotation(Translation.class);
            if (t != null) annotated.add(f);
        }
        if (annotated.isEmpty()) return;

        for (Field target : annotated) {
            Translation t = target.getAnnotation(Translation.class);
            Field refField = fieldMap.get(t.ref());
            if (refField == null) {
                log.debug("@Translation 引用字段 {} 不存在 (VO: {})", t.ref(), clazz.getSimpleName());
                continue;
            }
            Object idValue = refField.get(vo);
            if (idValue == null) {
                target.set(vo, null);
                continue;
            }
            String id = String.valueOf(idValue);
            String extension = t.dictType();
            String cacheKey = buildKey(t.type().name(), extension, id);
            String label = cache != null ? cache.get(cacheKey) : null;
            if (label == null) {
                TranslationService svc = registry.resolve(t);
                if (svc == null) {
                    log.debug("未注册的翻译器: type={} translator={}", t.type(), t.translator());
                    target.set(vo, null);
                    continue;
                }
                label = svc.translate(id, extension);
                if (cache != null && label != null) {
                    cache.put(cacheKey, label);
                }
            }
            target.set(vo, label);
        }
    }

    private static String buildKey(String type, String extension, String id) {
        if (extension == null || extension.isEmpty()) {
            return type + ":" + id;
        }
        return type + ":" + extension + ":" + id;
    }

    /**
     * 批量预热: 提前翻译一批 ID, 加速后续 vo 翻译
     * 用法: 知道一批 ID 范围, 先预热 cache, 再调 translate
     */
    public static void warmup(Collection<String> ids, TranslationType type, String dictType,
                              TranslationRegistry registry, TranslationCache cache) {
        if (ids == null || ids.isEmpty() || registry == null) return;
        TranslationService svc = registry.resolve(ofType(type));
        if (svc == null) return;
        for (String id : ids) {
            if (id == null) continue;
            String key = buildKey(type.name(), dictType, id);
            if (cache != null && cache.get(key) != null) continue;
            String label = svc.translate(id, dictType);
            if (cache != null && label != null) cache.put(key, label);
        }
    }

    private static Translation ofType(TranslationType t) {
        // helper for warmup: dummy annotation-like
        return new Translation() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() {
                return Translation.class;
            }
            @Override public TranslationType type() {
                return t;
            }
            @Override public String ref() {
                return "";
            }
            @Override public String dictType() {
                return "";
            }
            @Override public String translator() {
                return "";
            }
        };
    }
}
