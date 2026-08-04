package com.yutong.sample.mobile.dto;

import com.yutong.sample.request.domain.ApprovalRecord;
import com.yutong.sample.request.domain.BizRequestItem;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 移动端申请单详情 VO。设计来源: 18-样例业务详细设计
 * 轻量字段，便于移动端渲染。
 */
public record MobileRequestDetailVO(
        String id,
        String requestNo,
        String title,
        String customerId,
        String customerName,
        String requestStatus,
        String requestStatusLabel,
        BigDecimal totalAmount,
        String applyReason,
        String applicantName,
        OffsetDateTime submittedTime,
        OffsetDateTime approvedTime,
        Integer version,
        List<BizRequestItem> items,
        List<ApprovalRecord> approvals
) {
}
