package com.yutong.lowcode.rule.model;

/**
 * 规则类型枚举。设计来源: 36-低代码高级能力设计 (line 42-49)。
 *
 * <p>低代码表单引擎支持 6 类规则:
 * <ul>
 *   <li>{@link #VALIDATION}  校验: 表达式为 false 时返回错误消息</li>
 *   <li>{@link #VISIBILITY}  显隐: 表达式结果决定 targetField 是否可见</li>
 *   <li>{@link #LINKAGE}     联动: 表达式结果为 Map，合并到上下文触发联动</li>
 *   <li>{@link #COMPUTATION} 计算: 表达式结果赋给 targetField</li>
 *   <li>{@link #DEFAULT_VALUE} 默认值: targetField 为空时求值赋值</li>
 *   <li>{@link #READONLY}    只读: 表达式结果决定 targetField 是否只读</li>
 * </ul>
 */
public enum RuleType {
    VALIDATION,
    VISIBILITY,
    LINKAGE,
    COMPUTATION,
    DEFAULT_VALUE,
    READONLY
}
