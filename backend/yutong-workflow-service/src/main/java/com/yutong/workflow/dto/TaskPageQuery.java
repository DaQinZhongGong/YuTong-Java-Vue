package com.yutong.workflow.dto;

import lombok.Data;

/**
 * 任务分页查询参数。
 * <p>GA2-44: 不继承 PageRequest (record final), 使用独立 pageNo/pageSize 字段 (对齐 InventoryPageQuery 范式)。
 */
@Data
public class TaskPageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;

    /** 流程定义 key 筛选 */
    private String processKey;

    /** 业务类型筛选 */
    private String bizType;

    /** 业务单号模糊查询 */
    private String bizNo;

    /** 任务状态筛选 (PENDING/COMPLETED/REJECTED/DELEGATED/TRANSFERRED/CANCELLED) */
    private String taskStatus;

    /** 仅查询当前用户待办 (true: 我的待办; false/null: 全部任务) */
    private Boolean myTodoOnly;

    /** 仅查询当前用户已办 (true: 我的已办) */
    private Boolean myDoneOnly;
}
