package com.yutong.sample.payment.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 支付回调日志。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单。
 *
 * <p>GA2-41 落地: 每次第三方回调记录一条, 用于审计 + 幂等控制。
 *
 * <p>验签流程:
 * <ol>
 *   <li>接收回调 raw payload + signature + timestamp</li>
 *   <li>使用渠道配置的 secretKey 重算 HMAC-SHA256 (签名串 = orderNo + "\n" + channel + "\n" + amount + "\n" + timestamp)</li>
 *   <li>对比签名, 设置 verify_result = SUCCESS / FAILED</li>
 *   <li>失败则 process_result = IGNORED, 不修改支付单状态</li>
 * </ol>
 *
 * <p>幂等控制: idempotency_key = orderNo + "|" + callbackAction + "|" + channelTradeNo, 唯一索引兜底。
 */
@Getter
@Setter
@TableName("pay_callback_log")
public class PayCallbackLog extends BaseEntity {
    /** 关联 pay_order.id */
    private String orderId;
    /** 支付单号 (冗余, 便于日志检索) */
    private String orderNo;
    /** 回调渠道 (与 pay_order.channel 一致) */
    private String channel;
    /** 第三方流水号 */
    private String channelTradeNo;
    /** 回调动作: PAY_SUCCESS / PAY_FAIL / REFUND_SUCCESS / REFUND_FAIL */
    private String callbackAction;
    /** 接收到的签名 (十六进制) */
    private String signature;
    /** 时间戳 (来自第三方, 用于验签) */
    private String callbackTimestamp;
    /** 原始 payload (字符串快照, 用于审计追溯) */
    private String rawPayload;
    /** 解析后的 payload (JSON 字符串, 包含 orderNo/amount/channelTradeNo 等) */
    private String parsedPayload;
    /** 验签结果: SUCCESS / FAILED */
    private String verifyResult;
    /** 处理结果: PROCESSED / IGNORED / ERROR */
    private String processResult;
    /** 处理说明 (如重复回调忽略 / 状态机不匹配 / 系统错误) */
    private String processMessage;
    /** 幂等键 (orderNo + "|" + callbackAction + "|" + channelTradeNo) */
    private String idempotencyKey;
    /** 回调接收时间 */
    private OffsetDateTime receivedTime;

    /** callback_action 常量 */
    public static final String ACTION_PAY_SUCCESS = "PAY_SUCCESS";
    public static final String ACTION_PAY_FAIL = "PAY_FAIL";
    public static final String ACTION_REFUND_SUCCESS = "REFUND_SUCCESS";
    public static final String ACTION_REFUND_FAIL = "REFUND_FAIL";

    /** verify_result 常量 */
    public static final String VERIFY_SUCCESS = "SUCCESS";
    public static final String VERIFY_FAILED = "FAILED";

    /** process_result 常量 */
    public static final String PROCESS_PROCESSED = "PROCESSED";
    public static final String PROCESS_IGNORED = "IGNORED";
    public static final String PROCESS_ERROR = "ERROR";
}
