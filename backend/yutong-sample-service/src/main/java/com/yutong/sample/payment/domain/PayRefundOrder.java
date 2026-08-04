package com.yutong.sample.payment.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 退款单。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单。
 *
 * <p>GA2-41 落地: 一笔支付单可发起多次部分退款, 累计退款金额不超过原支付金额。
 *
 * <p>状态机: PENDING → SUCCESS / FAILED
 */
@Getter
@Setter
@TableName("pay_refund_order")
public class PayRefundOrder extends BaseEntity {
    /** 退款单号 (RFyyyyMMddNNNNNN 格式) */
    private String refundNo;
    /** 原支付单 ID */
    private String originalOrderId;
    /** 原支付单号 (冗余) */
    private String originalOrderNo;
    /** 退款金额 (分, 不超过原支付金额 - 已退款金额) */
    private Long refundAmount;
    /** 退款原因 */
    private String reason;
    /** 退款状态: PENDING / SUCCESS / FAILED */
    private String status;
    /** 操作人 (发起退款的用户) */
    private String operatorId;
    /** 第三方退款流水号 */
    private String channelRefundNo;
    /** 退款完成时间 */
    private OffsetDateTime refundedTime;
    /** 失败原因 */
    private String failReason;

    /** status 常量 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";
}
