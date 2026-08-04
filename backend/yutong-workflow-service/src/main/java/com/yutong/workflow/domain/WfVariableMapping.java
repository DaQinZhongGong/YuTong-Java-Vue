package com.yutong.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 工作流流程变量映射。设计来源: 41-工作流与BPMN引擎设计 (与低代码集成章节)。
 *
 * <p>用途: UserTask 可绑定 lc_page 表单页 page_code, 流程变量与实体字段映射。
 * 映射方向: IN (业务->流程变量) / OUT (流程变量->业务实体)。
 *
 * <p>GA2-44 L1+L2 轻量自研引擎实现: 当前仅落地 IN 方向 (业务实体字段->流程变量),
 * OUT 方向预留为后续版本增强 (流程变量回写业务实体)。
 */
@Getter
@Setter
@TableName("wf_variable_mapping")
public class WfVariableMapping extends BaseEntity {

    public static final String DIRECTION_IN = "IN";
    public static final String DIRECTION_OUT = "OUT";

    private String processKey;
    /** BPMN 节点 ID (NULL 表示流程级变量, 全流程可用) */
    private String nodeId;

    /** 流程变量名 (BPMN XML 表达式中引用, 如 totalAmount) */
    private String variableName;
    /** 业务实体字段路径 (如 totalAmount / customerId / items[0].productId) */
    private String entityFieldPath;
    /** 映射方向 IN/OUT */
    private String direction;
    /** 默认值 (业务实体字段为空时使用) */
    private String defaultValue;
}
