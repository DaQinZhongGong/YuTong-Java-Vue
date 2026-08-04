package com.yutong.workflow.dto;

import com.yutong.workflow.domain.WfProcessInstance;
import com.yutong.workflow.domain.WfTaskExt;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 流程实例详情 VO。包含实例基本信息 + 任务历史列表。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class InstanceDetailVO extends WfProcessInstance {

    /** 流程定义名称 */
    private String processDefinitionName;

    /** 任务历史列表 (按 create_time 升序, 含 PENDING/COMPLETED/REJECTED/DELEGATED/TRANSFERRED/CANCELLED 所有状态) */
    private List<WfTaskExt> tasks;

    /** 流程图高亮节点 ID 列表 (当前节点 + 已完成节点) */
    private List<String> highlightNodeIds;

    /** BPMN XML (前端渲染流程图) */
    private String bpmnXml;
}
