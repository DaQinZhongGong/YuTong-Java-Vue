package com.yutong.ai.aiflow.service;

import com.yutong.ai.aiflow.domain.AiflowDefinition;
import com.yutong.ai.aiflow.mapper.AiflowDefinitionMapper;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Component;

/**
 * DB 子流程定义解析器 — 从 aiflow_definition 取 dag_json。
 * 设计来源: ADR 0004 P2-B (Sub-Workflow 节点)。
 *
 * <p>失败关闭: 定义不存在 → 404; 非 PUBLISHED (DRAFT/ARCHIVED) → 400, 草稿/归档流程不可作为子流程运行;
 * dag_json 为空 → 400。
 */
@Component
public class DbSubflowDefinitionProvider implements SubflowDefinitionProvider {

    private final AiflowDefinitionMapper definitionMapper;

    public DbSubflowDefinitionProvider(AiflowDefinitionMapper definitionMapper) {
        this.definitionMapper = definitionMapper;
    }

    @Override
    public String resolveDagJson(String definitionId) {
        AiflowDefinition def = definitionMapper.selectById(definitionId);
        if (def == null) {
            throw new ResourceNotFoundException("子流程定义不存在: " + definitionId);
        }
        if (!AiflowDefinition.STATUS_PUBLISHED.equals(def.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "子流程未发布, 不可运行: " + definitionId);
        }
        if (def.getDagJson() == null || def.getDagJson().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "子流程定义为空: " + definitionId);
        }
        return def.getDagJson();
    }
}
