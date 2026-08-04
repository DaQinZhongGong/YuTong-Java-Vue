package com.yutong.workflow.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.workflow.domain.WfProcessInstance;
import com.yutong.workflow.domain.WfProcessDefinition;
import com.yutong.workflow.domain.WfTaskExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量工作流引擎自研实现。设计来源: 41-工作流与BPMN引擎设计。
 *
 * <p>GA2-44 L1+L2 落地: 不依赖 Flowable, 通过 BPMN XML 解析 + 状态机驱动流程流转。
 *
 * <p>核心算法:
 * <ul>
 *   <li>启动: 从 StartEvent 出发, 沿 SequenceFlow 前进到下一个 UserTask/ServiceTask/EndEvent</li>
 *   <li>办理通过: 当前 UserTask COMPLETED, 沿 SequenceFlow 前进到下一节点
 *       <ul>
 *           <li>UserTask: 生成新 PENDING 任务, 实例 currentNodeNames 更新</li>
 *           <li>ExclusiveGateway: 求值出边条件表达式, 选择第一个为 true 的出边前进</li>
 *           <li>ParallelGateway: (L4 暂未完整实现, 当前简化为单入单出)</li>
 *           <li>ServiceTask: 执行白名单服务 (Mock), 继续前进</li>
 *           <li>EndEvent: 实例 COMPLETED, 触发 business_callback_url 回调</li>
 *       </ul>
 *   </li>
 *   <li>驳回: 当前 UserTask REJECTED, 流程回退到上一 UserTask (重新生成 PENDING 任务);
 *       若为首个 UserTask 则实例 TERMINATED</li>
 *   <li>终止: 实例 TERMINATED, 所有 PENDING 任务 CANCELLED</li>
 * </ul>
 *
 * <p>事务边界: 由调用方 WorkflowApplicationService 控制 (@Transactional)。
 */
@Component
public class LightWorkflowEngineImpl implements LightWorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(LightWorkflowEngineImpl.class);

    private final ObjectMapper objectMapper;

    public LightWorkflowEngineImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public WfProcessInstance startProcess(WfProcessDefinitionHolder holder,
                                          String bizType, String bizId, String bizNo,
                                          String starterId, Map<String, Object> variables,
                                          String businessCallbackUrl) {
        WfProcessDefinition definition = holder.definition();
        BpmnProcess process = holder.bpmnProcess();

        if (!WfProcessDefinition.STATUS_PUBLISHED.equals(definition.getStatus())) {
            throw new BusinessException(ErrorCode.WF_DEFINITION_NOT_PUBLISHED,
                    "Process definition " + definition.getProcessKey() + " is not PUBLISHED");
        }

        // 创建流程实例
        String instanceId = IdGenerator.nextId();
        WfProcessInstance instance = new WfProcessInstance();
        instance.setId(instanceId);
        instance.setEngineInstanceId(instanceId);
        instance.setProcessDefinitionId(definition.getId());
        instance.setProcessKey(definition.getProcessKey());
        instance.setBizType(bizType);
        instance.setBizId(bizId);
        instance.setBizNo(bizNo);
        instance.setStarterId(starterId);
        instance.setInstanceStatus(WfProcessInstance.STATUS_RUNNING);
        instance.setVariables(toJson(variables));
        instance.setBusinessCallbackUrl(businessCallbackUrl);
        instance.setStartTime(OffsetDateTime.now());

        // 从 StartEvent 出发, 前进到下一个节点
        List<WfTaskExt> newTasks = advanceFromNode(process, instance, process.getStartEventId(), variables);
        if (!newTasks.isEmpty()) {
            instance.setCurrentNodeNames(joinNodeNames(newTasks));
        } else {
            // 启动后直接到达 EndEvent (无 UserTask 的简单流程), 实例完成
            instance.setInstanceStatus(WfProcessInstance.STATUS_COMPLETED);
            instance.setEndTime(OffsetDateTime.now());
            instance.setCurrentNodeNames("end");
        }

        log.info("Workflow instance started: instanceId={} processKey={} bizType={} bizId={} status={}",
                instanceId, definition.getProcessKey(), bizType, bizId, instance.getInstanceStatus());
        return instance;
    }

    @Override
    public List<WfTaskExt> completeTask(WfTaskExt task, WfProcessInstance instance,
                                        WfProcessDefinitionHolder holder,
                                        String actualHandler, String opinion,
                                        Map<String, Object> formData,
                                        Map<String, Object> variableUpdates) {
        BpmnProcess process = holder.bpmnProcess();

        // 更新任务状态
        task.setTaskStatus(WfTaskExt.STATUS_COMPLETED);
        task.setOpinion(opinion);
        task.setActualHandlerId(actualHandler);
        task.setCompleteTime(OffsetDateTime.now());
        if (formData != null && !formData.isEmpty()) {
            task.setFormDataJson(toJson(formData));
        }

        // 更新流程变量
        Map<String, Object> variables = parseVariables(instance.getVariables());
        if (variableUpdates != null) {
            variables.putAll(variableUpdates);
            instance.setVariables(toJson(variables));
        }

        // 流程前进到下一节点
        List<WfTaskExt> newTasks = advanceFromNode(process, instance, task.getNodeId(), variables);
        if (newTasks.isEmpty()) {
            // 到达 EndEvent, 实例完成
            instance.setInstanceStatus(WfProcessInstance.STATUS_COMPLETED);
            instance.setEndTime(OffsetDateTime.now());
            instance.setCurrentNodeNames("end");
        } else {
            instance.setCurrentNodeNames(joinNodeNames(newTasks));
        }

        log.info("Workflow task completed: taskId={} nodeId={} instanceId={} newTasks={}",
                task.getId(), task.getNodeId(), instance.getId(), newTasks.size());
        return newTasks;
    }

    @Override
    public List<WfTaskExt> rejectTask(WfTaskExt task, WfProcessInstance instance,
                                      WfProcessDefinitionHolder holder,
                                      String actualHandler, String opinion,
                                      Map<String, Object> variableUpdates) {
        BpmnProcess process = holder.bpmnProcess();

        // 更新任务状态
        task.setTaskStatus(WfTaskExt.STATUS_REJECTED);
        task.setOpinion(opinion);
        task.setActualHandlerId(actualHandler);
        task.setCompleteTime(OffsetDateTime.now());

        // 更新流程变量
        Map<String, Object> variables = parseVariables(instance.getVariables());
        if (variableUpdates != null) {
            variables.putAll(variableUpdates);
            instance.setVariables(toJson(variables));
        }

        // 查找上一 UserTask (回退路径)
        String previousUserTaskId = findPreviousUserTask(process, task.getNodeId());
        if (previousUserTaskId == null) {
            // 首个 UserTask 被驳回: 实例终止
            instance.setInstanceStatus(WfProcessInstance.STATUS_TERMINATED);
            instance.setEndTime(OffsetDateTime.now());
            instance.setCurrentNodeNames("rejected");
            instance.setTerminateReason("Rejected at first user task: " + opinion);
            log.info("Workflow instance terminated by reject: instanceId={} taskId={} opinion={}",
                    instance.getId(), task.getId(), opinion);
            return List.of();
        }

        // 生成新的 PENDING 任务 (回退到上一 UserTask)
        BpmnProcess.BpmnNode previousNode = process.getNode(previousUserTaskId);
        WfTaskExt newTask = createTaskForNode(previousNode, instance, variables);
        instance.setCurrentNodeNames(newTask.getTaskName());

        log.info("Workflow task rejected, rollback to previous: taskId={} instanceId={} previousNodeId={}",
                task.getId(), instance.getId(), previousUserTaskId);
        return List.of(newTask);
    }

    @Override
    public List<WfTaskExt> terminateInstance(WfProcessInstance instance, String reason) {
        instance.setInstanceStatus(WfProcessInstance.STATUS_TERMINATED);
        instance.setEndTime(OffsetDateTime.now());
        instance.setCurrentNodeNames("terminated");
        instance.setTerminateReason(reason);
        log.info("Workflow instance terminated: instanceId={} reason={}", instance.getId(), reason);
        // 调用方负责更新所有 PENDING 任务为 CANCELLED
        return List.of();
    }

    /**
     * 从指定节点出发, 前进到下一个 UserTask 或 EndEvent, 生成新任务。
     * <p>遇到 ServiceTask 自动执行 (Mock), 继续前进。
     * <p>遇到 ExclusiveGateway 求值出边条件, 选择第一个为 true 的出边前进。
     */
    private List<WfTaskExt> advanceFromNode(BpmnProcess process, WfProcessInstance instance,
                                            String fromNodeId, Map<String, Object> variables) {
        List<WfTaskExt> newTasks = new ArrayList<>();
        List<BpmnProcess.BpmnSequenceFlow> outFlows = process.getOutgoingFlows(fromNodeId);

        for (BpmnProcess.BpmnSequenceFlow flow : outFlows) {
            String targetNodeId = flow.getTargetRef();
            BpmnProcess.BpmnNode targetNode = process.getNode(targetNodeId);
            if (targetNode == null) {
                throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                        "Flow target node not found: " + targetNodeId);
            }

            switch (targetNode.getType()) {
                case USER_TASK -> {
                    WfTaskExt newTask = createTaskForNode(targetNode, instance, variables);
                    newTasks.add(newTask);
                }
                case END_EVENT -> {
                    // 到达 EndEvent, 实例即将 COMPLETED, 不生成新任务
                    log.info("Workflow reached EndEvent: instanceId={} endNodeId={}",
                            instance.getId(), targetNodeId);
                }
                case EXCLUSIVE_GATEWAY -> {
                    // 求值出边条件, 选择第一个为 true 的出边前进
                    List<BpmnProcess.BpmnSequenceFlow> gatewayOutFlows = process.getOutgoingFlows(targetNodeId);
                    boolean matched = false;
                    for (BpmnProcess.BpmnSequenceFlow gwFlow : gatewayOutFlows) {
                        if (ExpressionEvaluator.evaluateCondition(gwFlow.getConditionExpression(), variables)) {
                            // 递归前进
                            newTasks.addAll(advanceFromNode(process, instance, targetNodeId, variables));
                            matched = true;
                            break;
                        }
                    }
                    if (!matched) {
                        throw new BusinessException(ErrorCode.WF_BPMN_XML_INVALID,
                                "ExclusiveGateway " + targetNodeId + " no matched outgoing flow");
                    }
                }
                case PARALLEL_GATEWAY -> {
                    // 简化实现: 单入单出, 等同于直接通过
                    newTasks.addAll(advanceFromNode(process, instance, targetNodeId, variables));
                }
                case SERVICE_TASK -> {
                    // ServiceTask 自动执行 (Mock, 当前版本不实际调用, 仅记录日志)
                    log.info("Workflow ServiceTask executed (mock): instanceId={} nodeId={} target={}",
                            instance.getId(), targetNodeId, targetNode.getServiceTaskTarget());
                    newTasks.addAll(advanceFromNode(process, instance, targetNodeId, variables));
                }
                case START_EVENT -> {
                    // 不应该发生
                    throw new BusinessException(ErrorCode.WF_INTERNAL_ERROR,
                            "Cannot advance to StartEvent: " + targetNodeId);
                }
            }
        }

        return newTasks;
    }

    private WfTaskExt createTaskForNode(BpmnProcess.BpmnNode node, WfProcessInstance instance,
                                        Map<String, Object> variables) {
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
        // 解析办理人表达式 ${variable} -> 流程变量值
        String assignee = ExpressionEvaluator.resolveVariable(node.getAssigneeExpr(), variables);
        task.setAssigneeId(assignee != null ? assignee : node.getAssigneeExpr());
        task.setCandidateGroup(node.getCandidateGroups());
        task.setTaskStatus(WfTaskExt.STATUS_PENDING);
        task.setTaskType(WfTaskExt.TASK_TYPE_APPROVAL);
        task.setCreateTime(OffsetDateTime.now());
        // SLA: 默认 24 小时
        task.setDueTime(OffsetDateTime.now().plusHours(24));
        return task;
    }

    /**
     * 查找指定节点的上一个 UserTask (回退路径)。
     * <p>简化实现: 沿 SequenceFlow 反向遍历, 找到第一个 UserTask。
     * 不支持 ParallelGateway 复杂回退 (L4 留待后续版本)。
     */
    private String findPreviousUserTask(BpmnProcess process, String currentNodeId) {
        return findPreviousUserTaskDFS(process, currentNodeId, new java.util.HashSet<>());
    }

    private String findPreviousUserTaskDFS(BpmnProcess process, String currentNodeId, java.util.Set<String> visited) {
        if (visited.contains(currentNodeId)) return null;
        visited.add(currentNodeId);

        List<BpmnProcess.BpmnSequenceFlow> inFlows = process.getIncomingFlows(currentNodeId);
        for (BpmnProcess.BpmnSequenceFlow flow : inFlows) {
            String sourceNodeId = flow.getSourceRef();
            BpmnProcess.BpmnNode sourceNode = process.getNode(sourceNodeId);
            if (sourceNode == null) continue;

            if (sourceNode.getType() == BpmnProcess.BpmnNodeType.USER_TASK) {
                return sourceNodeId;
            }
            // 跳过 StartEvent (没有上一节点)
            if (sourceNode.getType() == BpmnProcess.BpmnNodeType.START_EVENT) {
                return null;
            }
            // 网关/ServiceTask: 继续反向遍历
            String found = findPreviousUserTaskDFS(process, sourceNodeId, visited);
            if (found != null) return found;
        }
        return null;
    }

    private String joinNodeNames(List<WfTaskExt> tasks) {
        if (tasks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tasks.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(tasks.get(i).getTaskName());
        }
        return sb.toString();
    }

    private String toJson(Map<String, Object> variables) {
        try {
            return objectMapper.writeValueAsString(variables == null ? new HashMap<>() : variables);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.WF_INTERNAL_ERROR,
                    "Failed to serialize variables: " + e.getMessage());
        }
    }

    private Map<String, Object> parseVariables(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("Failed to parse variables: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
