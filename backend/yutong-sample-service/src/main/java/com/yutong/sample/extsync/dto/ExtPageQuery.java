package com.yutong.sample.extsync.dto;

import lombok.Data;

/**
 * 外部接口同步分页查询参数。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 */
@Data
public class ExtPageQuery {

    private Integer pageNo = 1;
    private Integer pageSize = 20;

    /** 按任务 ID 过滤 (record/error 查询用) */
    private String taskId;

    /** 按状态过滤 */
    private String status;

    /** 按记录编号/系统编码模糊匹配 */
    private String keyword;
}
