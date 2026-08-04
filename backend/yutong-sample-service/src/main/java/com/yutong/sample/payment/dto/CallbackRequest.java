package com.yutong.sample.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 第三方支付回调请求。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)。
 *
 * <p>回调验签: HMAC-SHA256, 签名串 = orderNo + "\n" + channel + "\n" + amount + "\n" + timestamp。
 *
 * <p>回调幂等: idempotencyKey = orderNo + "|" + callbackAction + "|" + channelTradeNo, 唯一索引兜底。
 */
@Data
public class CallbackRequest {

    /** 支付单号 (POyyyyMMddNNNNNN 格式) */
    @NotBlank(message = "支付单号不能为空")
    private String orderNo;

    /** 支付渠道: MOCK_ALIPAY / MOCK_WECHAT / MOCK_UNIONPAY */
    @NotBlank(message = "支付渠道不能为空")
    private String channel;

    /** 第三方流水号 */
    @Size(max = 64, message = "第三方流水号长度不能超过 64")
    private String channelTradeNo;

    /** 回调动作: PAY_SUCCESS / PAY_FAIL / REFUND_SUCCESS / REFUND_FAIL */
    @NotBlank(message = "回调动作不能为空")
    private String callbackAction;

    /** 支付金额 (单位: 分, 来自第三方, 用于验签) */
    private Long amount;

    /** 时间戳 (来自第三方, 用于验签, yyyyMMddHHmmss 格式) */
    @NotBlank(message = "回调时间戳不能为空")
    private String callbackTimestamp;

    /** 接收到的签名 (十六进制 HMAC-SHA256) */
    @Size(max = 256, message = "签名长度不能超过 256")
    private String signature;

    /** 原始 payload (字符串快照, 用于审计追溯) */
    private String rawPayload;

    /** 失败原因 (callbackAction=PAY_FAIL 时填) */
    @Size(max = 512, message = "失败原因长度不能超过 512")
    private String failReason;
}
