package com.yutong.system.notification.dto;

import lombok.Data;

/**
 * 分发日志分页查询参数。GA2-40 实时通知 P2 落地。
 */
@Data
public class DispatchLogPageQuery {
    private Integer pageNo = 1;
    private Integer pageSize = 20;
    /** 分发状态筛选 (可选) */
    private String dispatchStatus;
    /** 渠道筛选 (可选) */
    private String channel;
    /** 接收人筛选 (可选) */
    private String receiverId;
}
