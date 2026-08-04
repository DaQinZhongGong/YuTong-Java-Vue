package com.yutong.sample.request.dto;

import com.yutong.sample.request.domain.ApprovalRecord;
import com.yutong.sample.request.domain.BizRequest;
import com.yutong.sample.request.domain.BizRequestItem;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 申请单详情响应。设计来源: 18-样例业务详细设计 API 契约
 * 包含主表全部字段 + 明细列表 + 审批记录列表。
 */
@Getter
@Setter
public class BizRequestDetailVO extends BizRequest {

    private List<BizRequestItem> items;

    private List<ApprovalRecord> approvals;
}
