package com.yutong.workflow.engine;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN 流程定义解析结果。设计来源: 41-工作流与BPMN引擎设计 (BPMN 首个实现版本必须支持的元素)。
 *
 * <p>GA2-44 L1+L2 轻量自研引擎实现: 仅支持 7 种核心元素:
 * <ul>
 *   <li>StartEvent: 流程开始</li>
 *   <li>UserTask: 人工审批</li>
 *   <li>ExclusiveGateway: 条件分支 (XOR 网关, 选择第一个条件为 true 的出边)</li>
 *   <li>ParallelGateway: 并行会签 (AND 网关, 所有入边到达后激活所有出边)</li>
 *   <li>ServiceTask: 调用内部服务 (受白名单约束)</li>
 *   <li>EndEvent: 结束</li>
 *   <li>SequenceFlow: 连线, 支持条件表达式</li>
 * </ul>
 *
 * <p>不支持: InclusiveGateway (OR 网关), ComplexGateway, EventSubprocess, CallActivity, SubProcess (L4 留待后续版本)。
 */
@Getter
@ToString
public class BpmnProcess {

    /** 流程 ID (BPMN XML 中 process id 属性) */
    private final String processId;

    /** 流程名称 */
    private final String processName;

    /** 节点列表 (按 BPMN XML 顺序): key=节点 ID, value=节点定义 */
    private final Map<String, BpmnNode> nodes = new LinkedHashMap<>();

    /** 连线列表 (按 BPMN XML 顺序) */
    private final List<BpmnSequenceFlow> flows = new ArrayList<>();

    /** StartEvent 节点 ID (流程入口) */
    private String startEventId;

    public BpmnProcess(String processId, String processName) {
        this.processId = processId;
        this.processName = processName;
    }

    public void addNode(BpmnNode node) {
        nodes.put(node.getId(), node);
        if (node.getType() == BpmnNodeType.START_EVENT) {
            this.startEventId = node.getId();
        }
    }

    public void addFlow(BpmnSequenceFlow flow) {
        flows.add(flow);
    }

    public BpmnNode getNode(String nodeId) {
        return nodes.get(nodeId);
    }

    /** 获取从指定节点出发的所有连线 */
    public List<BpmnSequenceFlow> getOutgoingFlows(String nodeId) {
        List<BpmnSequenceFlow> result = new ArrayList<>();
        for (BpmnSequenceFlow flow : flows) {
            if (flow.getSourceRef().equals(nodeId)) {
                result.add(flow);
            }
        }
        return result;
    }

    /** 获取指向指定节点的所有连线 */
    public List<BpmnSequenceFlow> getIncomingFlows(String nodeId) {
        List<BpmnSequenceFlow> result = new ArrayList<>();
        for (BpmnSequenceFlow flow : flows) {
            if (flow.getTargetRef().equals(nodeId)) {
                result.add(flow);
            }
        }
        return result;
    }

    @Getter
    @Setter
    @ToString
    public static class BpmnNode {
        /** 节点 ID (BPMN XML 中 id 属性) */
        private final String id;
        /** 节点名称 (BPMN XML 中 name 属性) */
        private final String name;
        /** 节点类型 */
        private final BpmnNodeType type;
        /** 办理人表达式 (仅 UserTask, 如 ${starter_manager}) */
        private String assigneeExpr;
        /** 候选组 (仅 UserTask, 如 role_dept_manager) */
        private String candidateGroups;
        /** 服务任务目标 (仅 ServiceTask, 如 message/send) */
        private String serviceTaskTarget;
        /** 节点描述 (BPMN documentation 元素) */
        private String documentation;

        public BpmnNode(String id, String name, BpmnNodeType type) {
            this.id = id;
            this.name = name;
            this.type = type;
        }

        public static BpmnNode of(String id, String name, BpmnNodeType type) {
            return new BpmnNode(id, name, type);
        }
    }

    public enum BpmnNodeType {
        START_EVENT,
        USER_TASK,
        SERVICE_TASK,
        EXCLUSIVE_GATEWAY,
        PARALLEL_GATEWAY,
        END_EVENT
    }

    @Getter
    @Setter
    @ToString
    public static class BpmnSequenceFlow {
        private final String id;
        private final String sourceRef;
        private final String targetRef;
        /** 条件表达式 (ExclusiveGateway 出边, 如 ${totalAmount > 10000}) */
        private String conditionExpression;

        public BpmnSequenceFlow(String id, String sourceRef, String targetRef) {
            this.id = id;
            this.sourceRef = sourceRef;
            this.targetRef = targetRef;
        }
    }
}
