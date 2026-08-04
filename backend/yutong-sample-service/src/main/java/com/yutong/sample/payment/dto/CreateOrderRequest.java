package com.yutong.sample.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建支付单请求。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)。
 *
 * <p>幂等键 idempotencyKey 由业务方携带, 防止重复创建支付单。
 */
@Data
public class CreateOrderRequest {

    /** 业务类型: biz_request / contract / work_ticket 等 */
    @NotBlank(message = "业务类型不能为空")
    @Size(max = 32, message = "业务类型长度不能超过 32")
    private String bizType;

    /** 业务 ID (关联业务方记录) */
    @Size(max = 64, message = "业务 ID 长度不能超过 64")
    private String bizId;

    /** 支付渠道: MOCK_ALIPAY / MOCK_WECHAT / MOCK_UNIONPAY */
    @NotBlank(message = "支付渠道不能为空")
    @Size(max = 32, message = "支付渠道长度不能超过 32")
    private String channel;

    /** 金额 (单位: 分, 必须 > 0) */
    @NotNull(message = "金额不能为空")
    @Min(value = 1, message = "金额必须大于 0")
    private Long amount;

    /** 币种 (ISO 4217, 默认 CNY) */
    @Size(max = 8, message = "币种长度不能超过 8")
    private String currency;

    /** 订单标题 */
    @NotBlank(message = "订单标题不能为空")
    @Size(max = 256, message = "订单标题长度不能超过 256")
    private String subject;

    /** 支付人 ID */
    @Size(max = 64, message = "支付人 ID 长度不能超过 64")
    private String payerId;

    /** 幂等键 (业务方调用时携带, 防止重复创建支付单) */
    @Size(max = 128, message = "幂等键长度不能超过 128")
    private String idempotencyKey;

    /** 渠道凭证 JSON (Mock 模式存放 accessKey/secretKey) */
    private String channelConfig;

    /** 额外参数 JSON (透传给第三方, 如 openid/return_url) */
    private String extraParams;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
