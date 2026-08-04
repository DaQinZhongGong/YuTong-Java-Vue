package com.yutong.sample.payment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 退款请求。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)。
 *
 * <p>累计退款金额不超过原支付金额。
 */
@Data
public class RefundRequest {

    /** 原支付单 ID */
    @NotBlank(message = "原支付单 ID 不能为空")
    private String originalOrderId;

    /** 退款金额 (单位: 分, 必须 > 0) */
    @NotNull(message = "退款金额不能为空")
    @Min(value = 1, message = "退款金额必须大于 0")
    private Long refundAmount;

    /** 退款原因 */
    @NotBlank(message = "退款原因不能为空")
    @Size(max = 256, message = "退款原因长度不能超过 256")
    private String reason;

    /** 备注 */
    @Size(max = 500, message = "备注长度不能超过 500")
    private String remark;
}
