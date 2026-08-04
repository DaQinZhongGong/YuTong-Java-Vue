package com.yutong.sample.workbench.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 工作台金额 Top N 项 VO。设计来源: 42-报表与大屏可视化设计 R0 验收标准（基础 ECharts 图表）。
 * <p>
 * 对齐 42 号文档内置数据集 {@code biz_request_amount_top10}（金额 Top10，SQL）。
 * 前端柱状图 xField=requestNo, yField=totalAmount。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkbenchTopItemVO {

    /** 申请单号 */
    private String requestNo;

    /** 申请单标题 */
    private String title;

    /** 合计金额 */
    private BigDecimal totalAmount;

    /** 客户名称快照 */
    private String customerNameSnapshot;

    /** 申请单状态 */
    private String requestStatus;
}
