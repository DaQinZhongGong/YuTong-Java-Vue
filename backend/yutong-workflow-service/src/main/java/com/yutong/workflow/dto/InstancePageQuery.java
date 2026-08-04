package com.yutong.workflow.dto;

import lombok.Data;

/**
 * 流程实例分页查询参数。
 * <p>GA2-44: 不继承 PageRequest (record final), 使用独立 pageNo/pageSize 字段 (对齐 InventoryPageQuery 范式)。
 */
@Data
public class InstancePageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;

    /** 流程定义 key 筛选 */
    private String processKey;

    /** 业务类型筛选 */
    private String bizType;

    /** 业务单号模糊查询 */
    private String bizNo;

    /** 发起人筛选 */
    private String starterId;

    /** 实例状态筛选 (RUNNING/COMPLETED/TERMINATED/SUSPENDED) */
    private String instanceStatus;
}
