package com.yutong.sample.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 对账文件导入请求。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单 (GA2-41)。
 *
 * <p>对账文件为 CSV/JSON 格式, 每行包含: orderNo, channelTradeNo, amount, status, paidTime。
 *
 * <p>对账逻辑:
 * <ol>
 *   <li>遍历对账文件每条记录, 在本地 pay_order 表查找匹配</li>
 *   <li>matched: 本地有 + 第三方有, 金额/状态一致</li>
 *   <li>mismatched: 本地有 + 第三方有, 但金额/状态不一致</li>
 *   <li>missing: 本地缺失 (第三方有 + 本地无)</li>
 *   <li>extra: 本地多余 (本地有 + 第三方无, 由反向扫描得出)</li>
 * </ol>
 */
@Data
public class ReconciliationImportRequest {

    /** 对账日期 */
    @NotNull(message = "对账日期不能为空")
    private LocalDate reconDate;

    /** 对账渠道: MOCK_ALIPAY / MOCK_WECHAT / MOCK_UNIONPAY */
    @NotBlank(message = "对账渠道不能为空")
    private String channel;

    /** 对账文件名 (用于审计) */
    private String fileName;

    /** 对账文件明细列表 */
    @NotNull(message = "对账明细不能为空")
    private List<ReconciliationItem> items;

    /**
     * 对账文件单条记录。
     */
    @Data
    public static class ReconciliationItem {
        /** 支付单号 */
        private String orderNo;
        /** 第三方流水号 */
        private String channelTradeNo;
        /** 金额 (单位: 分) */
        private Long amount;
        /** 状态 (PAID/REFUNDED 等) */
        private String status;
    }
}
