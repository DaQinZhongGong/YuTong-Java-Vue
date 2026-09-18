package com.yutong.ai.aiflow.service;

/**
 * 子流程定义解析器 — 按 definitionId 返回子 DAG 的 dag_json。
 * 设计来源: ADR 0004 P2-B (Sub-Workflow 节点)。
 *
 * <p>引擎默认不持有解析器 (definitionId 引用失败关闭);
 * 生产实现见 {@link DbSubflowDefinitionProvider} (仅 PUBLISHED 可运行)。
 * 单测可注入 stub。
 */
public interface SubflowDefinitionProvider {

    /**
     * 解析子流程定义的 dag_json。
     *
     * @param definitionId 子流程定义 ID
     * @return dag_json (nodes/edges 结构)
     * @throws com.yutong.common.exception.BusinessException 不存在/不可运行时抛错 (失败关闭)
     */
    String resolveDagJson(String definitionId);
}
