package com.yutong.workflow.engine;

import com.yutong.workflow.domain.WfProcessInstance;
import com.yutong.workflow.domain.WfTaskExt;

import java.util.List;
import java.util.Map;

/**
 * 轻量工作流引擎接口。设计来源: 41-工作流与BPMN引擎设计 (WorkflowAdapter)。
 *
 * <p>GA2-44 L1+L2 轻量自研引擎实现: 屏蔽 Flowable/Camunda 差异, 业务代码只调用本接口, 不直连引擎 API。
 * 当前实现: {@link LightWorkflowEngineImpl} (自研, 不依赖 Flowable)。
 * 后续可替换: FlowableWorkflowEngineImpl (集成 Flowable 7.x)。
 */
public interface LightWorkflowEngine {

    /**
     * 启动流程实例。
     *
     * @param definition         流程定义 (PUBLISHED 状态)
     * @param bizType            业务类型
     * @param bizId              业务主键
     * @param bizNo              业务单号
     * @param starterId          发起人 ID
     * @param variables          流程变量 (启动时传入)
     * @param businessCallbackUrl 业务回调 URL (流程结束时回调)
     * @return 流程实例
     */
    WfProcessInstance startProcess(WfProcessDefinitionHolder definition,
                                   String bizType, String bizId, String bizNo,
                                   String starterId, Map<String, Object> variables,
                                   String businessCallbackUrl);

    /**
     * 完成任务, 流程前进到下一节点。
     *
     * @param task        待办任务 (PENDING 状态)
     * @param instance    流程实例 (RUNNING 状态)
     * @param definition  流程定义
     * @param actualHandler 实际办理人 (委托场景下与 assigneeId 不同)
     * @param opinion     办理意见
     * @param formData    办理表单数据
     * @param variableUpdates 流程变量更新
     * @return 新生成的 PENDING 任务列表 (流程前进到下一 UserTask; 若到达 EndEvent 则返回空列表)
     */
    List<WfTaskExt> completeTask(WfTaskExt task, WfProcessInstance instance,
                                 WfProcessDefinitionHolder definition,
                                 String actualHandler, String opinion,
                                 Map<String, Object> formData,
                                 Map<String, Object> variableUpdates);

    /**
     * 驳回任务, 流程回退到上一 UserTask 或终止实例。
     *
     * @param task        待办任务 (PENDING 状态)
     * @param instance    流程实例 (RUNNING 状态)
     * @param definition  流程定义
     * @param actualHandler 实际办理人
     * @param opinion     驳回意见 (必填)
     * @param variableUpdates 流程变量更新
     * @return 新生成的 PENDING 任务列表 (回退到上一 UserTask); 若为首个 UserTask 则实例 TERMINATED, 返回空列表
     */
    List<WfTaskExt> rejectTask(WfTaskExt task, WfProcessInstance instance,
                               WfProcessDefinitionHolder definition,
                               String actualHandler, String opinion,
                               Map<String, Object> variableUpdates);

    /**
     * 终止流程实例。
     *
     * @param instance 流程实例
     * @param reason   终止原因
     * @return 被取消的 PENDING 任务列表
     */
    List<WfTaskExt> terminateInstance(WfProcessInstance instance, String reason);

    /**
     * 流程定义持有者 (避免与 domain WfProcessDefinition 命名冲突)。
     * 包含解析后的 BPMN 流程对象 + 流程定义元数据。
     */
    record WfProcessDefinitionHolder(
            com.yutong.workflow.domain.WfProcessDefinition definition,
            BpmnProcess bpmnProcess
    ) {}
}
