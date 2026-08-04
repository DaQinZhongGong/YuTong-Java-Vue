package com.yutong.sample.mobile.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 移动端待办 VO。设计来源: 18-样例业务详细设计
 * 聚合待办表和申请单摘要字段。
 */
public record MobileTodoVO(
        String id,
        String bizType,
        String bizId,
        String requestNo,
        String title,
        String customerName,
        BigDecimal totalAmount,
        OffsetDateTime submittedTime,
        String statusLabel,
        Integer version
) {
}
