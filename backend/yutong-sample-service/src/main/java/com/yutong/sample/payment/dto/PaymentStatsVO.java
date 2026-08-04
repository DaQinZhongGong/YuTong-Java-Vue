package com.yutong.sample.payment.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 支付监控统计 VO。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)。
 *
 * <p>仪表盘展示:
 * <ul>
 *   <li>totalOrders: 支付单总数</li>
 *   <li>statusCounts: 按状态分布 (PENDING/PAID/FAILED/CANCELLED/REFUNDING/REFUNDED/CLOSED)</li>
 *   <li>channelCounts: 按渠道分布 (MOCK_ALIPAY/MOCK_WECHAT/MOCK_UNIONPAY)</li>
 *   <li>totalAmountPaid: 已支付总金额 (分)</li>
 *   <li>totalAmountRefunded: 已退款总金额 (分)</li>
 *   <li>callbackTotal: 回调日志总数</li>
 *   <li>callbackVerifyFailed: 验签失败数</li>
 *   <li>reconMatchedCount: 对账匹配成功数</li>
 *   <li>reconMismatchedCount: 对账不一致数</li>
 * </ul>
 */
@Data
public class PaymentStatsVO {
    /** 支付单总数 */
    private long totalOrders;
    /** 按状态分布 */
    private Map<String, Long> statusCounts = new HashMap<>();
    /** 按渠道分布 */
    private Map<String, Long> channelCounts = new HashMap<>();
    /** 已支付总金额 (分) */
    private long totalAmountPaid;
    /** 已退款总金额 (分) */
    private long totalAmountRefunded;
    /** 退款单总数 */
    private long totalRefunds;
    /** 回调日志总数 */
    private long callbackTotal;
    /** 验签失败数 */
    private long callbackVerifyFailed;
    /** 对账匹配成功数 */
    private long reconMatchedCount;
    /** 对账不一致数 */
    private long reconMismatchedCount;
    /** 对账记录总数 */
    private long reconTotal;
}
