package com.yutong.sample.extsync.dto;

import lombok.Data;

/**
 * 触发同步请求。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>手动触发指定任务的同步执行。
 */
@Data
public class TriggerSyncRequest {

    /** 触发方式 (默认 MANUAL; 系统调度自动填充 SCHEDULED) */
    private String triggerType;

    /** 业务键过滤 (可选, 仅同步指定业务键的数据) */
    private String businessKeyFilter;

    /** 自定义请求参数 (覆盖任务默认 request_template, 仅当次触发有效) */
    private String customPayload;
}
