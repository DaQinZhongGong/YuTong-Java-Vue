package com.yutong.lowcode.rule.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则集合求值结果。设计来源: 36-低代码高级能力设计。
 *
 * <p>按规则类型分组输出，供前端表单引擎消费:
 * <ul>
 *   <li>{@code validations}  校验失败消息列表 (VALIDATION 规则求值 false)</li>
 *   <li>{@code visibility}   字段可见性 (VISIBILITY)</li>
 *   <li>{@code computations} 字段计算结果 (COMPUTATION)</li>
 *   <li>{@code defaults}     字段默认值 (DEFAULT_VALUE，仅原值为空时填充)</li>
 *   <li>{@code readonly}     字段只读状态 (READONLY)</li>
 *   <li>{@code linkage}      联动字段更新 (LINKAGE，合并 Map 后的字段值)</li>
 * </ul>
 */
public record RuleEvalResult(
        List<String> validations,
        Map<String, Boolean> visibility,
        Map<String, Object> computations,
        Map<String, Object> defaults,
        Map<String, Boolean> readonly,
        Map<String, Object> linkage
) {

    /** 创建空结果 (内部集合可变，供 RuleEvaluator 填充)。 */
    public static RuleEvalResult empty() {
        return new RuleEvalResult(
                new ArrayList<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>(),
                new LinkedHashMap<>());
    }
}
