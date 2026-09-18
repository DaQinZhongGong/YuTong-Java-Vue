package com.yutong.common.translation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译服务注册表 — 单例,持有所有 TranslationService bean
 * 设计来源: ADR 0004 P2-A common-translation
 *
 * 落点:84-字典/用户/部门显示名自动化详设
 *
 * 用法 (业务模块启动时注入):
 *   @Bean
 *   public TranslationRegistry translationRegistry(
 *       DictTranslationService dictSvc,
 *       UserTranslationService userSvc,
 *       DeptTranslationService deptSvc) {
 *       TranslationRegistry reg = new TranslationRegistry();
 *       reg.register(TranslationType.DICT, dictSvc);
 *       reg.register(TranslationType.USER, userSvc);
 *       reg.register(TranslationType.DEPT, deptSvc);
 *       return reg;
 *   }
 *
 * CUSTOM 翻译:
 *   reg.registerByName("myDictTranslator", myDictTranslator);
 */
public class TranslationRegistry {

    /** type -> service */
    private final Map<TranslationType, TranslationService> byType = new ConcurrentHashMap<>();
    /** 自定义 named service */
    private final Map<String, TranslationService> byName = new ConcurrentHashMap<>();

    public void register(TranslationType type, TranslationService service) {
        if (type == null || service == null) return;
        byType.put(type, service);
    }

    public void registerByName(String name, TranslationService service) {
        if (name == null || name.isBlank() || service == null) return;
        byName.put(name, service);
    }

    /**
     * 解析翻译器:优先按 @Translation(translator) 名称查,否则按 type 查
     */
    public TranslationService resolve(Translation t) {
        if (t == null) return null;
        if (!t.translator().isBlank()) {
            return byName.get(t.translator());
        }
        return byType.get(t.type());
    }

    /** 当前已注册的 type (用于诊断 / 健康检查) */
    public List<TranslationType> registeredTypes() {
        return List.copyOf(byType.keySet());
    }

    /** 当前已注册的 named service (诊断用) */
    public List<String> registeredNames() {
        return List.copyOf(byName.keySet());
    }

    /** 诊断:返回所有 type/name 映射 (不可变 snapshot) */
    public Map<String, String> snapshot() {
        Map<String, String> out = new HashMap<>();
        byType.forEach((k, v) -> out.put("type:" + k, v.getClass().getName()));
        byName.forEach((k, v) -> out.put("name:" + k, v.getClass().getName()));
        return out;
    }
}
