package com.yutong.sample.payment.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 支付订单。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单。
 *
 * <p>GA2-41 落地: 验证支付单状态机 + 第三方回调验签 + 回调幂等 + 对账文件导入 + 金额精度 + 安全审计。
 *
 * <p>状态机:
 * <ul>
 *   <li>PENDING → PAID (支付成功)</li>
 *   <li>PENDING → FAILED (支付失败终态)</li>
 *   <li>PENDING → CANCELLED (用户取消终态)</li>
 *   <li>PAID → REFUNDING (退款中)</li>
 *   <li>REFUNDING → REFUNDED (全部退款完成) / 回退 PAID (部分退款完成)</li>
 *   <li>PAID/REFUNDED → CLOSED (关闭)</li>
 * </ul>
 *
 * <p>金额精度: 统一使用 bigint 存储分单位 (1 元 = 100 分), 避免 BigDecimal 浮点精度问题。
 */
@Getter
@Setter
@TableName("pay_order")
public class PayOrder extends BaseEntity {
    /** 支付单号 (业务可读, POyyyyMMddNNNNNN 格式) */
    private String orderNo;
    /** 业务类型: biz_request / contract / work_ticket 等 */
    private String bizType;
    /** 业务 ID (关联业务方记录) */
    private String bizId;
    /** 支付渠道: MOCK_ALIPAY / MOCK_WECHAT / MOCK_UNIONPAY */
    private String channel;
    /** 金额 (单位: 分, 1 元 = 100 分) */
    private Long amount;
    /** 币种 (ISO 4217, 默认 CNY) */
    private String currency;
    /** 订单标题 */
    private String subject;
    /** 支付人 ID */
    private String payerId;
    /** 支付状态 */
    private String status;
    /** 第三方流水号 (支付成功后回写) */
    private String channelTradeNo;
    /** 支付时间 */
    private OffsetDateTime paidTime;
    /** 订单过期时间 (默认 30 分钟) */
    private OffsetDateTime expiredTime;
    /** 关闭时间 */
    private OffsetDateTime closedTime;
    /** 失败原因 (status=FAILED 时填) */
    private String failReason;
    /** 幂等键 (业务方调用 createOrder 时携带, 防止重复创建支付单) */
    private String idempotencyKey;
    /** 渠道凭证 (Mock 模式存放 accessKey/secretKey 的 JSON, 生产应走 SecretManager) */
    private String channelConfig;
    /** 额外参数 (透传给第三方, 如 openid/return_url) */
    private String extraParams;

    /** status 常量 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_REFUNDING = "REFUNDING";
    public static final String STATUS_REFUNDED = "REFUNDED";
    public static final String STATUS_CLOSED = "CLOSED";

    /** channel 常量 */
    public static final String CHANNEL_MOCK_ALIPAY = "MOCK_ALIPAY";
    public static final String CHANNEL_MOCK_WECHAT = "MOCK_WECHAT";
    public static final String CHANNEL_MOCK_UNIONPAY = "MOCK_UNIONPAY";

    /** biz_type 常量 */
    public static final String BIZ_TYPE_REQUEST = "biz_request";
}
