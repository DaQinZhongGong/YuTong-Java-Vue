package com.yutong.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageResult;
import com.yutong.workflow.domain.WfProcessDefinition;
import com.yutong.workflow.domain.WfProcessInstance;
import com.yutong.workflow.domain.WfTaskExt;
import com.yutong.workflow.dto.*;
import com.yutong.workflow.engine.BpmnProcess;
import com.yutong.workflow.engine.BpmnXmlParser;
import com.yutong.workflow.engine.LightWorkflowEngine;
import com.yutong.workflow.mapper.WfProcessDefinitionMapper;
import com.yutong.workflow.mapper.WfProcessInstanceMapper;
import com.yutong.workflow.mapper.WfTaskExtMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工作流应用服务。设计来源: 41-工作流与BPMN引擎设计 (API 契约)。
 *
 * <p>GA2-44 L1+L2 落地: 编排 domain + engine, 提供给 Controller 调用。
 *
 * <p>关键能力:
 * <ul>
 *   <li>流程定义 CRUD + 发布 + 部署</li>
 *   <li>流程实例启动 + 详情 + 终止</li>
 *   <li>任务列表 (我的待办/我的已办/全部) + 办理通过 + 驳回 + 委派 + 转办</li>
 *   <li>运营监控统计</li>
 * </ul>
 */
@Service
public class WorkflowApplicationService {

    public static final String RESOURCE_CODE = "workflow";

    private static final Logger log = LoggerFactory.getLogger(WorkflowApplicationService.class);

    private final WfProcessDefinitionMapper definitionMapper;
    private final WfProcessInstanceMapper instanceMapper;
    private final WfTaskExtMapper taskMapper;
    private final BpmnXmlParser bpmnXmlParser;
    private final LightWorkflowEngine engine;
    private final ObjectMapper objectMapper;
    private final DataScopeResolver dataScopeResolver;

    public WorkflowApplicationService(WfProcessDefinitionMapper definitionMapper,
                                      WfProcessInstanceMapper instanceMapper,
                                      WfTaskExtMapper taskMapper,
                                      BpmnXmlParser bpmnXmlParser,
                                      LightWorkflowEngine engine,
                                      ObjectMapper objectMapper,
                                      DataScopeResolver dataScopeResolver) {
        this.definitionMapper = definitionMapper;
        this.instanceMapper = instanceMapper;
        this.taskMapper = taskMapper;
        this.bpmnXmlParser = bpmnXmlParser;
        this.engine = engine;
        this.objectMapper = objectMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    // ===== 流程定义 =====

    public PageResult<WfProcessDefinition> pageDefinitions(com.yutong.common.response.PageRequest request,
                                                            String keyword, String status, String bizType) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<WfProcessDefinition> wrapper = new LambdaQueryWrapper<WfProcessDefinition>()
                .eq(WfProcessDefinition::getTenantId, CurrentUserContext.getTenantId())
                .like(keyword != null && !keyword.isBlank(), WfProcessDefinition::getProcessName, keyword)
                .eq(status != null && !status.isBlank(), WfProcessDefinition::getStatus, status)
                .eq(bizType != null && !bizType.isBlank(), WfProcessDefinition::getBizType, bizType)
                .orderByDesc(WfProcessDefinition::getCreatedTime);
        applyDefinitionDataScope(wrapper, scope);
        Page<WfProcessDefinition> page = definitionMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    public WfProcessDefinition getDefinition(String id) {
        WfProcessDefinition def = definitionMapper.selectById(id);
        if (def == null || (def.getDeleted() != null && def.getDeleted())) {
            throw new ResourceNotFoundException(ErrorCode.WF_DEFINITION_NOT_FOUND, "Workflow definition not found: " + id);
        }
        return def;
    }

    @Transactional
    public WfProcessDefinition createDefinition(SaveProcessDefinitionRequest request) {
        // 校验 process_key 唯一性 (同 tenant + 未删除)
        Long existsCount = definitionMapper.selectCount(new LambdaQueryWrapper<WfProcessDefinition>()
                .eq(WfProcessDefinition::getTenantId, CurrentUserContext.getTenantId())
                .eq(WfProcessDefinition::getProcessKey, request.getProcessKey()));
        if (existsCount > 0) {
            throw new BusinessException(ErrorCode.WF_DEFINITION_KEY_DUPLICATE,
                    "Process key already exists: " + request.getProcessKey());
        }

        // 解析 BPMN XML 校验
        BpmnProcess bpmnProcess = bpmnXmlParser.parse(request.getBpmnXml());

        WfProcessDefinition def = new WfProcessDefinition();
        def.setId(IdGenerator.nextId());
        def.setProcessKey(request.getProcessKey());
        def.setProcessName(request.getProcessName());
        def.setCategoryCode(request.getCategoryCode());
        def.setBizType(request.getBizType());
        def.setBpmnXml(request.getBpmnXml());
        def.setVersionNo(1);
        def.setStatus(WfProcessDefinition.STATUS_DRAFT);
        def.setFormPageCode(request.getFormPageCode());
        def.setDescription(request.getDescription());
        if (request.getServiceTaskWhitelist() != null) {
            def.setServiceTaskWhitelist(toJson(request.getServiceTaskWhitelist()));
        }
        definitionMapper.insert(def);
        log.info("Workflow definition created: id={} processKey={} bpmnProcessId={}",
                def.getId(), def.getProcessKey(), bpmnProcess.getProcessId());
        return def;
    }

    @Transactional
    public WfProcessDefinition updateDefinition(String id, SaveProcessDefinitionRequest request) {
        WfProcessDefinition def = getDefinition(id);
        if (WfProcessDefinition.STATUS_DISABLED.equals(def.getStatus())) {
            throw new BusinessException(ErrorCode.WF_DEFINITION_NOT_PUBLISHED,
                    "Cannot update DISABLED definition");
        }
        // PUBLISHED 仅可更新 description
        if (WfProcessDefinition.STATUS_PUBLISHED.equals(def.getStatus())) {
            def.setDescription(request.getDescription());
            definitionMapper.updateById(def);
            return def;
        }

        // DRAFT 可全量更新
        bpmnXmlParser.parse(request.getBpmnXml()); // 校验
        def.setProcessName(request.getProcessName());
        def.setCategoryCode(request.getCategoryCode());
        def.setBizType(request.getBizType());
        def.setBpmnXml(request.getBpmnXml());
        def.setFormPageCode(request.getFormPageCode());
        def.setDescription(request.getDescription());
        if (request.getServiceTaskWhitelist() != null) {
            def.setServiceTaskWhitelist(toJson(request.getServiceTaskWhitelist()));
        }
        definitionMapper.updateById(def);
        return def;
    }

    @Transactional
    public WfProcessDefinition publishDefinition(String id) {
        WfProcessDefinition def = getDefinition(id);
        // 仅 DRAFT/DEPLOYED 可发布: DEPLOYED 表示已通过 deploy 校验, 可直接发布
        String status = def.getStatus();
        if (!WfProcessDefinition.STATUS_DRAFT.equals(status)
                && !WfProcessDefinition.STATUS_DEPLOYED.equals(status)) {
            throw new BusinessException(ErrorCode.WF_DEFINITION_NOT_PUBLISHED,
                    "Only DRAFT/DEPLOYED can be published, current status: " + status);
        }

        // 解析校验
        bpmnXmlParser.parse(def.getBpmnXml());

        // 将同 process_key 的旧 PUBLISHED 版本置为 DISABLED
        List<WfProcessDefinition> oldPublished = definitionMapper.selectList(
                new LambdaQueryWrapper<WfProcessDefinition>()
                        .eq(WfProcessDefinition::getTenantId, def.getTenantId())
                        .eq(WfProcessDefinition::getProcessKey, def.getProcessKey())
                        .eq(WfProcessDefinition::getStatus, WfProcessDefinition.STATUS_PUBLISHED)
                        .ne(WfProcessDefinition::getId, def.getId()));
        for (WfProcessDefinition old : oldPublished) {
            old.setStatus(WfProcessDefinition.STATUS_DISABLED);
            definitionMapper.updateById(old);
        }

        // 计算新版本号: 同 process_key 最大版本号 + 1
        Integer maxVersion = definitionMapper.selectList(new LambdaQueryWrapper<WfProcessDefinition>()
                        .eq(WfProcessDefinition::getTenantId, def.getTenantId())
                        .eq(WfProcessDefinition::getProcessKey, def.getProcessKey())
                        .orderByDesc(WfProcessDefinition::getVersionNo)
                        .last("LIMIT 1"))
                .stream().findFirst().map(WfProcessDefinition::getVersionNo).orElse(0);
        def.setVersionNo(maxVersion + 1);
        def.setStatus(WfProcessDefinition.STATUS_PUBLISHED);
        def.setEngineDeploymentId(IdGenerator.nextId());
        definitionMapper.updateById(def);
        log.info("Workflow definition published: id={} processKey={} version={}",
                def.getId(), def.getProcessKey(), def.getVersionNo());
        return def;
    }

    /**
     * 部署流程定义: 校验 BPMN XML 合法性 + 校验服务任务白名单, 标记状态为 DEPLOYED。
     * <p>
     * 状态流转: DRAFT / DEPLOYED -> DEPLOYED (允许重复部署以重新校验)。
     * PUBLISHED / DISABLED 不可部署, 需先回退或新建版本。
     *
     * @param id 流程定义 ID
     * @return 部署后的流程定义 (status=DEPLOYED, engineDeploymentId 已填充)
     */
    @Transactional
    public WfProcessDefinition deployDefinition(String id) {
        WfProcessDefinition def = getDefinition(id);
        String status = def.getStatus();
        if (!WfProcessDefinition.STATUS_DRAFT.equals(status)
                && !WfProcessDefinition.STATUS_DEPLOYED.equals(status)) {
            throw new BusinessException(ErrorCode.WF_DEFINITION_NOT_PUBLISHED,
                    "Only DRAFT/DEPLOYED can be deployed, current status: " + status);
        }

        // 1. 校验 BPMN XML 合法性 (结构 + 必备元素)
        BpmnProcess bpmnProcess = bpmnXmlParser.parse(def.getBpmnXml());

        // 2. 校验服务任务白名单: BPMN 中所有 ServiceTask 的 target 必须在白名单内
        validateServiceTaskWhitelist(bpmnProcess, def.getServiceTaskWhitelist());

        // 3. 标记为 DEPLOYED, 填充引擎部署 ID (幂等: 已存在则保留)
        def.setStatus(WfProcessDefinition.STATUS_DEPLOYED);
        if (def.getEngineDeploymentId() == null || def.getEngineDeploymentId().isBlank()) {
            def.setEngineDeploymentId(IdGenerator.nextId());
        }
        definitionMapper.updateById(def);
        log.info("Workflow definition deployed: id={} processKey={} deploymentId={}",
                def.getId(), def.getProcessKey(), def.getEngineDeploymentId());
        return def;
    }

    /**
     * 校验 BPMN 中所有 ServiceTask 的 target 均在白名单内。
     * 白名单为空且存在 ServiceTask → 拒绝 (fail-closed, 防止未授权服务调用)。
     */
    private void validateServiceTaskWhitelist(BpmnProcess process, String whitelistJson) {
        List<String> whitelist = parseServiceTaskWhitelist(whitelistJson);
        for (BpmnProcess.BpmnNode node : process.getNodes().values()) {
            if (node.getType() != BpmnProcess.BpmnNodeType.SERVICE_TASK) {
                continue;
            }
            String target = node.getServiceTaskTarget();
            if (target == null || target.isBlank()) {
                // BpmnXmlParser.validate 已保证 ServiceTask 必须有 target, 此处兜底
                throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                        "ServiceTask " + node.getId() + " missing target attribute");
            }
            if (!whitelist.contains(target)) {
                throw new BusinessException(ErrorCode.WF_SERVICE_TASK_NOT_WHITELISTED,
                        "ServiceTask " + node.getId() + " target [" + target
                                + "] not in whitelist: " + whitelist);
            }
        }
    }

    /** 解析 serviceTaskWhitelist JSON 字符串为列表; null/空返回空列表。 */
    private List<String> parseServiceTaskWhitelist(String whitelistJson) {
        if (whitelistJson == null || whitelistJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(whitelistJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                    "Invalid serviceTaskWhitelist JSON: " + e.getMessage());
        }
    }

    @Transactional
    public void deleteDefinition(String id) {
        WfProcessDefinition def = getDefinition(id);
        if (WfProcessDefinition.STATUS_PUBLISHED.equals(def.getStatus())) {
            throw new BusinessException(ErrorCode.WF_DEFINITION_NOT_PUBLISHED,
                    "Cannot delete PUBLISHED definition, disable first");
        }
        definitionMapper.deleteById(id);
    }

    // ===== 流程实例 =====

    public PageResult<WfProcessInstance> pageInstances(InstancePageQuery query) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<WfProcessInstance> wrapper = new LambdaQueryWrapper<WfProcessInstance>()
                .eq(WfProcessInstance::getTenantId, CurrentUserContext.getTenantId())
                .eq(query.getProcessKey() != null && !query.getProcessKey().isBlank(),
                        WfProcessInstance::getProcessKey, query.getProcessKey())
                .eq(query.getBizType() != null && !query.getBizType().isBlank(),
                        WfProcessInstance::getBizType, query.getBizType())
                .like(query.getBizNo() != null && !query.getBizNo().isBlank(),
                        WfProcessInstance::getBizNo, query.getBizNo())
                .eq(query.getStarterId() != null && !query.getStarterId().isBlank(),
                        WfProcessInstance::getStarterId, query.getStarterId())
                .eq(query.getInstanceStatus() != null && !query.getInstanceStatus().isBlank(),
                        WfProcessInstance::getInstanceStatus, query.getInstanceStatus())
                .orderByDesc(WfProcessInstance::getCreatedTime);
        applyInstanceDataScope(wrapper, scope);
        Page<WfProcessInstance> page = instanceMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPageNo(), query.getPageSize());
    }

    public InstanceDetailVO getInstance(String id) {
        WfProcessInstance instance = instanceMapper.selectById(id);
        if (instance == null || (instance.getDeleted() != null && instance.getDeleted())) {
            throw new ResourceNotFoundException(ErrorCode.WF_INSTANCE_NOT_FOUND, "Workflow instance not found: " + id);
        }
        WfProcessDefinition def = getDefinition(instance.getProcessDefinitionId());

        InstanceDetailVO vo = new InstanceDetailVO();
        // 复制 instance 属性
        copyInstanceProperties(instance, vo);
        vo.setProcessDefinitionName(def.getProcessName());
        vo.setBpmnXml(def.getBpmnXml());

        // 查询任务历史
        List<WfTaskExt> tasks = taskMapper.selectList(new LambdaQueryWrapper<WfTaskExt>()
                .eq(WfTaskExt::getInstanceId, id)
                .orderByAsc(WfTaskExt::getCreateTime));
        vo.setTasks(tasks);

        // 高亮节点: 当前 PENDING + 已完成 COMPLETED
        List<String> highlightNodeIds = new ArrayList<>();
        for (WfTaskExt task : tasks) {
            if (WfTaskExt.STATUS_PENDING.equals(task.getTaskStatus()) ||
                    WfTaskExt.STATUS_COMPLETED.equals(task.getTaskStatus())) {
                if (!highlightNodeIds.contains(task.getNodeId())) {
                    highlightNodeIds.add(task.getNodeId());
                }
            }
        }
        vo.setHighlightNodeIds(highlightNodeIds);
        return vo;
    }

    @Transactional
    public WfProcessInstance startInstance(StartProcessRequest request) {
        // 查询 PUBLISHED 流程定义
        WfProcessDefinition def = definitionMapper.selectOne(new LambdaQueryWrapper<WfProcessDefinition>()
                .eq(WfProcessDefinition::getTenantId, CurrentUserContext.getTenantId())
                .eq(WfProcessDefinition::getProcessKey, request.getProcessKey())
                .eq(WfProcessDefinition::getStatus, WfProcessDefinition.STATUS_PUBLISHED)
                .last("LIMIT 1"));
        if (def == null) {
            throw new ResourceNotFoundException(ErrorCode.WF_DEFINITION_NOT_FOUND,
                    "PUBLISHED process definition not found: " + request.getProcessKey());
        }

        // 解析 BPMN
        BpmnProcess bpmnProcess = bpmnXmlParser.parse(def.getBpmnXml());
        LightWorkflowEngine.WfProcessDefinitionHolder holder =
                new LightWorkflowEngine.WfProcessDefinitionHolder(def, bpmnProcess);

        // 启动流程
        WfProcessInstance instance = engine.startProcess(holder,
                request.getBizType(), request.getBizId(), request.getBizNo(),
                CurrentUserContext.getUserId(), request.getVariables(),
                request.getBusinessCallbackUrl());
        instanceMapper.insert(instance);

        // 持久化新生成的 PENDING 任务
        // (engine.startProcess 不持久化任务, 由 service 层负责)
        if (WfProcessInstance.STATUS_RUNNING.equals(instance.getInstanceStatus())) {
            // 重新执行 advanceFromNode 逻辑会重复, 这里通过查询 currentNodeNames 反推
            // 简化方案: engine.startProcess 修改为返回 List<WfTaskExt>
            // 当前实现: 重新解析 currentNodeNames 不可行, 改为在 engine 内部持久化更复杂
            // 采用最佳实践: 在 startInstance 中重新调用 advance 逻辑生成任务
            List<WfTaskExt> newTasks = regenerateTasksForStart(holder, instance, request.getVariables());
            for (WfTaskExt task : newTasks) {
                taskMapper.insert(task);
            }
        }

        log.info("Workflow instance started: instanceId={} processKey={} bizType={} bizId={}",
                instance.getId(), request.getProcessKey(), request.getBizType(), request.getBizId());
        return instance;
    }

    /**
     * 启动流程后重新生成 PENDING 任务 (避免修改 engine 接口签名)。
     * 实际从 StartEvent 出发, 找到第一个 UserTask 生成任务。
     */
    private List<WfTaskExt> regenerateTasksForStart(LightWorkflowEngine.WfProcessDefinitionHolder holder,
                                                    WfProcessInstance instance,
                                                    Map<String, Object> variables) {
        // 利用 engine 内部逻辑: 直接调用 completeTask with task = StartEvent 的占位
        // 简化方案: 直接解析 BPMN 找到第一个 UserTask
        BpmnProcess process = holder.bpmnProcess();
        List<WfTaskExt> newTasks = new ArrayList<>();
        String startId = process.getStartEventId();
        collectUserTasksFromNode(process, instance, startId, variables, newTasks, new java.util.HashSet<>());
        return newTasks;
    }

    private void collectUserTasksFromNode(BpmnProcess process, WfProcessInstance instance,
                                          String fromNodeId, Map<String, Object> variables,
                                          List<WfTaskExt> newTasks, java.util.Set<String> visited) {
        if (visited.contains(fromNodeId)) return;
        visited.add(fromNodeId);
        for (BpmnProcess.BpmnSequenceFlow flow : process.getOutgoingFlows(fromNodeId)) {
            String targetId = flow.getTargetRef();
            BpmnProcess.BpmnNode node = process.getNode(targetId);
            if (node == null) continue;
            switch (node.getType()) {
                case USER_TASK -> {
                    String taskId = IdGenerator.nextId();
                    WfTaskExt task = new WfTaskExt();
                    task.setId(taskId);
                    task.setEngineTaskId(taskId);
                    task.setInstanceId(instance.getId());
                    task.setProcessKey(instance.getProcessKey());
                    task.setBizType(instance.getBizType());
                    task.setBizId(instance.getBizId());
                    task.setBizNo(instance.getBizNo());
                    task.setNodeId(node.getId());
                    task.setTaskName(node.getName());
                    String assignee = com.yutong.workflow.engine.ExpressionEvaluator.resolveVariable(
                            node.getAssigneeExpr(), variables);
                    task.setAssigneeId(assignee != null ? assignee : node.getAssigneeExpr());
                    task.setCandidateGroup(node.getCandidateGroups());
                    task.setTaskStatus(WfTaskExt.STATUS_PENDING);
                    task.setTaskType(WfTaskExt.TASK_TYPE_APPROVAL);
                    task.setCreateTime(OffsetDateTime.now());
                    task.setDueTime(OffsetDateTime.now().plusHours(24));
                    newTasks.add(task);
                }
                case END_EVENT -> { /* 实例完成, 不生成任务 */ }
                case EXCLUSIVE_GATEWAY, PARALLEL_GATEWAY, SERVICE_TASK ->
                        collectUserTasksFromNode(process, instance, targetId, variables, newTasks, visited);
                case START_EVENT -> { /* 不应该 */ }
            }
        }
    }

    @Transactional
    public WfProcessInstance terminateInstance(String id, TerminateInstanceRequest request) {
        WfProcessInstance instance = instanceMapper.selectById(id);
        if (instance == null) {
            throw new ResourceNotFoundException(ErrorCode.WF_INSTANCE_NOT_FOUND, "Instance not found: " + id);
        }
        if (!WfProcessInstance.STATUS_RUNNING.equals(instance.getInstanceStatus())) {
            throw new BusinessException(ErrorCode.WF_INSTANCE_COMPLETED,
                    "Instance already finished: " + instance.getInstanceStatus());
        }

        // 终止实例
        engine.terminateInstance(instance, request.getReason());
        instanceMapper.updateById(instance);

        // 取消所有 PENDING 任务
        List<WfTaskExt> pendingTasks = taskMapper.selectList(new LambdaQueryWrapper<WfTaskExt>()
                .eq(WfTaskExt::getInstanceId, id)
                .eq(WfTaskExt::getTaskStatus, WfTaskExt.STATUS_PENDING));
        for (WfTaskExt task : pendingTasks) {
            task.setTaskStatus(WfTaskExt.STATUS_CANCELLED);
            task.setCompleteTime(OffsetDateTime.now());
            task.setOpinion("Instance terminated: " + request.getReason());
            taskMapper.updateById(task);
        }

        log.info("Workflow instance terminated: instanceId={} reason={} cancelledTasks={}",
                id, request.getReason(), pendingTasks.size());
        return instance;
    }

    // ===== 任务 =====

    public PageResult<WfTaskExt> pageTasks(TaskPageQuery query) {
        String currentUserId = CurrentUserContext.getUserId();
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<WfTaskExt> wrapper = new LambdaQueryWrapper<WfTaskExt>()
                .eq(WfTaskExt::getTenantId, CurrentUserContext.getTenantId())
                .eq(query.getProcessKey() != null && !query.getProcessKey().isBlank(),
                        WfTaskExt::getProcessKey, query.getProcessKey())
                .eq(query.getBizType() != null && !query.getBizType().isBlank(),
                        WfTaskExt::getBizType, query.getBizType())
                .like(query.getBizNo() != null && !query.getBizNo().isBlank(),
                        WfTaskExt::getBizNo, query.getBizNo())
                .eq(query.getTaskStatus() != null && !query.getTaskStatus().isBlank(),
                        WfTaskExt::getTaskStatus, query.getTaskStatus())
                .orderByDesc(WfTaskExt::getCreateTime);

        // 我的待办
        if (Boolean.TRUE.equals(query.getMyTodoOnly())) {
            wrapper.eq(WfTaskExt::getTaskStatus, WfTaskExt.STATUS_PENDING)
                   .and(w -> w.eq(WfTaskExt::getAssigneeId, currentUserId)
                              .or().like(WfTaskExt::getAssigneeId, "%" + currentUserId + "%"));
        }
        // 我的已办
        if (Boolean.TRUE.equals(query.getMyDoneOnly())) {
            wrapper.ne(WfTaskExt::getTaskStatus, WfTaskExt.STATUS_PENDING)
                   .eq(WfTaskExt::getActualHandlerId, currentUserId);
        }
        // 非 admin 用户查看全部任务时，限制为只看自己相关的任务 (assignee 或 handler)
        if (scope != null
                && scope.scopeType() != DataScopeType.ALL
                && scope.scopeType() != DataScopeType.TENANT
                && !Boolean.TRUE.equals(query.getMyTodoOnly())
                && !Boolean.TRUE.equals(query.getMyDoneOnly())) {
            String scopeUserId = scope.userId();
            if (scopeUserId == null || scopeUserId.isBlank()) {
                wrapper.apply("1 = 0");
            } else {
                wrapper.and(w -> w.eq(WfTaskExt::getAssigneeId, scopeUserId)
                                   .or().eq(WfTaskExt::getActualHandlerId, scopeUserId));
            }
        }

        Page<WfTaskExt> page = taskMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPageNo(), query.getPageSize());
    }

    public WfTaskExt getTask(String id) {
        WfTaskExt task = taskMapper.selectById(id);
        if (task == null || (task.getDeleted() != null && task.getDeleted())) {
            throw new ResourceNotFoundException(ErrorCode.WF_TASK_NOT_FOUND, "Task not found: " + id);
        }
        return task;
    }

    @Transactional
    public WfTaskExt completeTask(String id, CompleteTaskRequest request) {
        WfTaskExt task = getTask(id);
        validateTaskPending(task);
        validateTaskAssignee(task);

        WfProcessInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new ResourceNotFoundException(ErrorCode.WF_INSTANCE_NOT_FOUND,
                    "Instance not found: " + task.getInstanceId());
        }
        if (!WfProcessInstance.STATUS_RUNNING.equals(instance.getInstanceStatus())) {
            throw new BusinessException(ErrorCode.WF_INSTANCE_COMPLETED,
                    "Instance not running: " + instance.getInstanceStatus());
        }

        WfProcessDefinition def = getDefinition(instance.getProcessDefinitionId());
        BpmnProcess bpmnProcess = bpmnXmlParser.parse(def.getBpmnXml());
        LightWorkflowEngine.WfProcessDefinitionHolder holder =
                new LightWorkflowEngine.WfProcessDefinitionHolder(def, bpmnProcess);

        // 调用引擎办理任务
        List<WfTaskExt> newTasks = engine.completeTask(task, instance, holder,
                CurrentUserContext.getUserId(), request.getOpinion(),
                request.getFormData(), request.getVariableUpdates());

        // 更新任务和实例
        taskMapper.updateById(task);
        instanceMapper.updateById(instance);

        // 持久化新生成的 PENDING 任务
        for (WfTaskExt newTask : newTasks) {
            taskMapper.insert(newTask);
        }

        log.info("Workflow task completed: taskId={} instanceId={} newPendingTasks={}",
                id, instance.getId(), newTasks.size());
        return task;
    }

    @Transactional
    public WfTaskExt rejectTask(String id, RejectTaskRequest request) {
        WfTaskExt task = getTask(id);
        validateTaskPending(task);
        validateTaskAssignee(task);

        WfProcessInstance instance = instanceMapper.selectById(task.getInstanceId());
        if (instance == null) {
            throw new ResourceNotFoundException(ErrorCode.WF_INSTANCE_NOT_FOUND,
                    "Instance not found: " + task.getInstanceId());
        }
        if (!WfProcessInstance.STATUS_RUNNING.equals(instance.getInstanceStatus())) {
            throw new BusinessException(ErrorCode.WF_INSTANCE_COMPLETED,
                    "Instance not running: " + instance.getInstanceStatus());
        }

        WfProcessDefinition def = getDefinition(instance.getProcessDefinitionId());
        BpmnProcess bpmnProcess = bpmnXmlParser.parse(def.getBpmnXml());
        LightWorkflowEngine.WfProcessDefinitionHolder holder =
                new LightWorkflowEngine.WfProcessDefinitionHolder(def, bpmnProcess);

        List<WfTaskExt> newTasks = engine.rejectTask(task, instance, holder,
                CurrentUserContext.getUserId(), request.getOpinion(), request.getVariableUpdates());

        taskMapper.updateById(task);
        instanceMapper.updateById(instance);

        for (WfTaskExt newTask : newTasks) {
            taskMapper.insert(newTask);
        }

        log.info("Workflow task rejected: taskId={} instanceId={} status={} rollbackTasks={}",
                id, instance.getId(), instance.getInstanceStatus(), newTasks.size());
        return task;
    }

    @Transactional
    public WfTaskExt delegateTask(String id, DelegateTaskRequest request) {
        WfTaskExt task = getTask(id);
        validateTaskPending(task);
        validateTaskAssignee(task);

        String delegateType = request.getDelegateType();
        if (delegateType == null || delegateType.isBlank()) {
            delegateType = WfTaskExt.DELEGATE_TYPE_DELEGATE;
        }

        String originalAssignee = task.getAssigneeId();
        task.setDelegateType(delegateType);
        task.setDelegateToUserId(request.getDelegateToUserId());
        task.setOpinion(request.getOpinion());

        if (WfTaskExt.DELEGATE_TYPE_TRANSFER.equals(delegateType)) {
            // 转办: 任务转到新人, 原办理人不再参与
            task.setTaskStatus(WfTaskExt.STATUS_TRANSFERRED);
            task.setCompleteTime(OffsetDateTime.now());
            task.setActualHandlerId(CurrentUserContext.getUserId());
            taskMapper.updateById(task);

            // 生成新的 PENDING 任务给新人
            WfTaskExt newTask = new WfTaskExt();
            String newTaskId = IdGenerator.nextId();
            newTask.setId(newTaskId);
            newTask.setEngineTaskId(newTaskId);
            newTask.setInstanceId(task.getInstanceId());
            newTask.setProcessKey(task.getProcessKey());
            newTask.setBizType(task.getBizType());
            newTask.setBizId(task.getBizId());
            newTask.setBizNo(task.getBizNo());
            newTask.setNodeId(task.getNodeId());
            newTask.setTaskName(task.getTaskName());
            newTask.setAssigneeId(request.getDelegateToUserId());
            newTask.setTaskStatus(WfTaskExt.STATUS_PENDING);
            newTask.setTaskType(task.getTaskType());
            newTask.setCreateTime(OffsetDateTime.now());
            newTask.setDueTime(OffsetDateTime.now().plusHours(24));
            newTask.setRemark("Transferred from " + originalAssignee);
            taskMapper.insert(newTask);
        } else {
            // 委派: 任务转到被委派人, 委派人办理后任务回到原办理人
            // 简化实现: 直接将 assigneeId 改为被委派人, delegateToUserId 记录原办理人
            // 实际生产场景应生成子任务, L4 留待后续版本
            task.setAssigneeId(request.getDelegateToUserId());
            task.setRemark("Delegated from " + originalAssignee + ", will return after completion");
            taskMapper.updateById(task);
        }

        log.info("Workflow task delegated: taskId={} type={} from={} to={}",
                id, delegateType, originalAssignee, request.getDelegateToUserId());
        return task;
    }

    // ===== 运营监控 =====

    public WorkflowStatsVO getStats() {
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();

        WorkflowStatsVO vo = new WorkflowStatsVO();
        vo.setPublishedDefinitionCount(definitionMapper.selectCount(new LambdaQueryWrapper<WfProcessDefinition>()
                .eq(WfProcessDefinition::getTenantId, tenantId)
                .eq(WfProcessDefinition::getStatus, WfProcessDefinition.STATUS_PUBLISHED)));

        vo.setTotalInstanceCount(instanceMapper.selectCount(new LambdaQueryWrapper<WfProcessInstance>()
                .eq(WfProcessInstance::getTenantId, tenantId)));
        vo.setRunningInstanceCount(instanceMapper.selectCount(new LambdaQueryWrapper<WfProcessInstance>()
                .eq(WfProcessInstance::getTenantId, tenantId)
                .eq(WfProcessInstance::getInstanceStatus, WfProcessInstance.STATUS_RUNNING)));
        vo.setCompletedInstanceCount(instanceMapper.selectCount(new LambdaQueryWrapper<WfProcessInstance>()
                .eq(WfProcessInstance::getTenantId, tenantId)
                .eq(WfProcessInstance::getInstanceStatus, WfProcessInstance.STATUS_COMPLETED)));
        vo.setTerminatedInstanceCount(instanceMapper.selectCount(new LambdaQueryWrapper<WfProcessInstance>()
                .eq(WfProcessInstance::getTenantId, tenantId)
                .eq(WfProcessInstance::getInstanceStatus, WfProcessInstance.STATUS_TERMINATED)));

        vo.setPendingTaskCount(taskMapper.selectCount(new LambdaQueryWrapper<WfTaskExt>()
                .eq(WfTaskExt::getTenantId, tenantId)
                .eq(WfTaskExt::getTaskStatus, WfTaskExt.STATUS_PENDING)));
        vo.setCompletedTaskCount(taskMapper.selectCount(new LambdaQueryWrapper<WfTaskExt>()
                .eq(WfTaskExt::getTenantId, tenantId)
                .ne(WfTaskExt::getTaskStatus, WfTaskExt.STATUS_PENDING)));

        vo.setMyPendingTaskCount(taskMapper.selectCount(new LambdaQueryWrapper<WfTaskExt>()
                .eq(WfTaskExt::getTenantId, tenantId)
                .eq(WfTaskExt::getTaskStatus, WfTaskExt.STATUS_PENDING)
                .eq(WfTaskExt::getAssigneeId, userId)));
        vo.setMyCompletedTaskCount(taskMapper.selectCount(new LambdaQueryWrapper<WfTaskExt>()
                .eq(WfTaskExt::getTenantId, tenantId)
                .eq(WfTaskExt::getActualHandlerId, userId)));

        return vo;
    }

    // ===== 私有校验方法 =====

    private void validateTaskPending(WfTaskExt task) {
        if (!WfTaskExt.STATUS_PENDING.equals(task.getTaskStatus())) {
            throw new BusinessException(ErrorCode.WF_TASK_ALREADY_COMPLETED,
                    "Task not PENDING: " + task.getTaskStatus());
        }
    }

    private void validateTaskAssignee(WfTaskExt task) {
        String userId = CurrentUserContext.getUserId();
        if (userId == null) return; // 无登录上下文 (如系统内部调用)
        if (task.getAssigneeId() == null) return;
        // 单人办理: 直接比较
        if (task.getAssigneeId().equals(userId)) return;
        // 多人办理: 逗号分隔
        if (task.getAssigneeId().contains(",")) {
            for (String a : task.getAssigneeId().split(",")) {
                if (a.trim().equals(userId)) return;
            }
        }
        // candidateGroup 校验 (Mock: 简化为不拦截, 实际应查询用户角色/部门)
        // 设计已知限制: 完整 candidateGroup 校验需接入 RBAC, 当前简化为 assigneeId 优先
        log.warn("Task assignee check skipped: taskId={} userId={} assigneeId={}",
                task.getId(), userId, task.getAssigneeId());
    }

    private void copyInstanceProperties(WfProcessInstance source, InstanceDetailVO target) {
        target.setId(source.getId());
        target.setTenantId(source.getTenantId());
        target.setEngineInstanceId(source.getEngineInstanceId());
        target.setProcessDefinitionId(source.getProcessDefinitionId());
        target.setProcessKey(source.getProcessKey());
        target.setBizType(source.getBizType());
        target.setBizId(source.getBizId());
        target.setBizNo(source.getBizNo());
        target.setStarterId(source.getStarterId());
        target.setInstanceStatus(source.getInstanceStatus());
        target.setCurrentNodeNames(source.getCurrentNodeNames());
        target.setVariables(source.getVariables());
        target.setBusinessCallbackUrl(source.getBusinessCallbackUrl());
        target.setTerminateReason(source.getTerminateReason());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setCreatedBy(source.getCreatedBy());
        target.setCreatedTime(source.getCreatedTime());
        target.setUpdatedBy(source.getUpdatedBy());
        target.setUpdatedTime(source.getUpdatedTime());
        target.setVersion(source.getVersion());
        target.setRemark(source.getRemark());
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.WF_INTERNAL_ERROR,
                    "Failed to serialize: " + e.getMessage());
        }
    }

    private void applyDefinitionDataScope(LambdaQueryWrapper<WfProcessDefinition> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(WfProcessDefinition::getCreatedBy, userId);
    }

    private void applyInstanceDataScope(LambdaQueryWrapper<WfProcessInstance> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(WfProcessInstance::getStarterId, userId);
    }
}
