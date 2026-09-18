package com.yutong.ai.aiflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.ai.aiflow.domain.AiflowDefinition;
import com.yutong.ai.aiflow.domain.AiflowInstance;
import com.yutong.ai.aiflow.mapper.AiflowDefinitionMapper;
import com.yutong.ai.aiflow.mapper.AiflowInstanceMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AIFlow 应用服务: CRUD + 发布 + 实例管理。
 * 设计来源: V040 aiflow_definition / aiflow_instance
 */
@Service
public class AiflowService {

    private static final Logger log = LoggerFactory.getLogger(AiflowService.class);

    private final AiflowDefinitionMapper definitionMapper;
    private final AiflowInstanceMapper instanceMapper;
    private final AiflowEngine engine;
    private final ObjectMapper objectMapper;

    public AiflowService(AiflowDefinitionMapper definitionMapper,
                         AiflowInstanceMapper instanceMapper,
                         AiflowEngine engine,
                         ObjectMapper objectMapper) {
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
        this.engine = engine;
        this.objectMapper = objectMapper;
    }

    public PageResult<AiflowDefinition> pageDefinitions(PageRequest request, String flowCode, String status) {
        String tenantId = CurrentUserContext.getTenantId();
        LambdaQueryWrapper<AiflowDefinition> wrapper = new LambdaQueryWrapper<AiflowDefinition>()
                .eq(AiflowDefinition::getTenantId, tenantId)
                .like(flowCode != null && !flowCode.isBlank(), AiflowDefinition::getFlowCode, flowCode)
                .eq(status != null && !status.isBlank(), AiflowDefinition::getStatus, status)
                .orderByDesc(AiflowDefinition::getCreatedTime);
        Page<AiflowDefinition> page = definitionMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public AiflowDefinition getDefinition(String id) {
        AiflowDefinition def = definitionMapper.selectById(id);
        if (def == null) throw new ResourceNotFoundException("AIFlow 定义不存在: " + id);
        return def;
    }

    @Transactional
    public AiflowDefinition saveDefinition(AiflowDefinition def) {
        if (def.getFlowCode() == null || def.getFlowCode().isBlank())
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "flowCode 不能为空");
        if (def.getFlowName() == null || def.getFlowName().isBlank())
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "flowName 不能为空");
        // dag_json 空兼容
        if (def.getDagJson() != null && def.getDagJson().isBlank()) def.setDagJson(null);
        // 校验 dag_json 合法性 (非空时解析)
        if (def.getDagJson() != null && !def.getDagJson().isBlank()) {
            try {
                engine.parseDag(def.getDagJson());
            } catch (BusinessException e) { throw e; } catch (Exception e) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "dag_json 非法: " + e.getMessage());
            }
        }
        if (def.getId() == null || def.getId().isBlank()) {
            def.setId(IdGenerator.nextId());
            def.setTenantId(CurrentUserContext.getTenantId());
            def.setCreatedBy(CurrentUserContext.getUserId());
            if (def.getVersionNo() == null) def.setVersionNo(1);
            def.setStatus(AiflowDefinition.STATUS_DRAFT);
            def.setVersion(0);
            try {
                definitionMapper.insert(def);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "流程编码+版本已存在: " + def.getFlowCode() + " v" + def.getVersionNo());
            }
            log.info("aiflow definition created: id={} code={} v{}", def.getId(), def.getFlowCode(), def.getVersionNo());
            return def;
        }
        AiflowDefinition existing = getDefinition(def.getId());
        checkVersion(def.getVersion(), existing.getVersion());
        if (!AiflowDefinition.STATUS_DRAFT.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 DRAFT 状态可编辑，当前状态=" + existing.getStatus());
        }
        def.setTenantId(existing.getTenantId());
        def.setStatus(AiflowDefinition.STATUS_DRAFT);
        def.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = definitionMapper.updateById(def);
        if (rows == 0) throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        return definitionMapper.selectById(def.getId());
    }

    @Transactional
    public AiflowDefinition publish(String id, Integer version) {
        AiflowDefinition def = getDefinition(id);
        checkVersion(version, def.getVersion());
        if (AiflowDefinition.STATUS_PUBLISHED.equals(def.getStatus()))
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "已发布，不可重复发布");
        if (!AiflowDefinition.STATUS_DRAFT.equals(def.getStatus()))
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 DRAFT 可发布，当前状态=" + def.getStatus());
        if (def.getDagJson() == null || def.getDagJson().isBlank())
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "dag_json 不能为空，发布前需完成编排");
        // 校验 DAG 拓扑无环
        var dag = engine.parseDag(def.getDagJson());
        engine.topologicalLevels(dag);
        def.setStatus(AiflowDefinition.STATUS_PUBLISHED);
        def.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = definitionMapper.updateById(def);
        if (rows == 0) throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        log.info("aiflow published: id={} code={} v{}", id, def.getFlowCode(), def.getVersionNo());
        return def;
    }

    @Transactional
    public AiflowDefinition archive(String id, Integer version) {
        AiflowDefinition def = getDefinition(id);
        checkVersion(version, def.getVersion());
        if (AiflowDefinition.STATUS_ARCHIVED.equals(def.getStatus())) return def;
        def.setStatus(AiflowDefinition.STATUS_ARCHIVED);
        def.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = definitionMapper.updateById(def);
        if (rows == 0) throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        return def;
    }

    @Transactional
    public void deleteDefinition(String id, Integer version) {
        AiflowDefinition def = getDefinition(id);
        checkVersion(version, def.getVersion());
        if (AiflowDefinition.STATUS_PUBLISHED.equals(def.getStatus()))
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "已发布定义不可删除，请先归档");
        definitionMapper.deleteById(id);
    }

    /**
     * 启动实例 (SSE 场景下由 Controller 异步调用本方法并透传 emitter)。
     * 同步创建 RUNNING 实例，执行引擎并更新为 SUCCESS/FAILED。
     */
    public AiflowInstance startInstance(String definitionId, String inputJson, Consumer<AiflowEngine.NodeStateEvent> sseEmitter) {
        AiflowDefinition def = getDefinition(definitionId);
        if (!AiflowDefinition.STATUS_PUBLISHED.equals(def.getStatus()))
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "仅 PUBLISHED 定义可执行，当前状态=" + def.getStatus());
        String tenantId = CurrentUserContext.getTenantId();
        AiflowInstance inst = new AiflowInstance();
        inst.setId(IdGenerator.nextId());
        inst.setTenantId(tenantId);
        inst.setFlowId(definitionId);
        inst.setStatus(AiflowInstance.STATUS_RUNNING);
        inst.setInputJson(inputJson != null ? inputJson : "{}");
        inst.setDagSnapshot(def.getDagJson());
        inst.setNodeStates("{}");
        inst.setCreatedBy(CurrentUserContext.getUserId());
        inst.setVersion(0);
        instanceMapper.insert(inst);

        Map<String, String> nodeStatus = new LinkedHashMap<>();
        Map<String, Object> outputs;
        try {
            outputs = engine.execute(def.getDagJson(), inputJson, event -> {
                nodeStatus.put(event.nodeId(), event.status());
                // 每次事件轻量更新 node_states (仅状态，不依赖 outputs)
                try {
                    String ns = engine.toNodeStatesJson(nodeStatus, Map.of());
                    inst.setNodeStates(ns);
                    instanceMapper.updateById(inst);
                } catch (Exception ignore) {}
                if (sseEmitter != null) sseEmitter.accept(event);
            });
            inst.setStatus(AiflowInstance.STATUS_SUCCESS);
            inst.setOutputJson(toJson(outputs));
            inst.setNodeStates(engine.toNodeStatesJson(nodeStatus, outputs));
            instanceMapper.updateById(inst);
        } catch (Exception e) {
            log.error("aiflow instance failed: flowId={} instId={}", definitionId, inst.getId(), e);
            inst.setStatus(AiflowInstance.STATUS_FAILED);
            inst.setOutputJson(toJson(Map.of("error", e.getMessage() != null ? e.getMessage() : "unknown")));
            inst.setNodeStates(engine.toNodeStatesJson(nodeStatus, Map.of()));
            instanceMapper.updateById(inst);
            throw e;
        }
        return inst;
    }

    public PageResult<AiflowInstance> pageInstances(String flowId, PageRequest request) {
        String tenantId = CurrentUserContext.getTenantId();
        LambdaQueryWrapper<AiflowInstance> wrapper = new LambdaQueryWrapper<AiflowInstance>()
                .eq(AiflowInstance::getTenantId, tenantId)
                .eq(flowId != null && !flowId.isBlank(), AiflowInstance::getFlowId, flowId)
                .orderByDesc(AiflowInstance::getCreatedTime);
        Page<AiflowInstance> page = instanceMapper.selectPage(new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public AiflowInstance getInstance(String id) {
        AiflowInstance inst = instanceMapper.selectById(id);
        if (inst == null) throw new ResourceNotFoundException("AIFlow 实例不存在: " + id);
        return inst;
    }

    private String toJson(Object obj) {
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return "{}"; }
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion))
            throw new BusinessConflictException("版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
    }
}
