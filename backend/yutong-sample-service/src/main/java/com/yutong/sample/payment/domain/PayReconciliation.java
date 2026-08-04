package com.yutong.sample.payment.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 对账记录。设计来源: 35-样例业务矩阵扩展设计 P2 支付订单。
 *
 * <p>GA2-41 落地: 每日按渠道对账, 比对本地支付单与第三方对账文件。
 *
 * <p>状态机: PENDING → MATCHED / MISMATCHED / IMPORTED / FAILED
 *
 * <p>对账维度:
 * <ul>
 *   <li>matched (匹配成功: 本地有 + 第三方有, 金额/状态一致)</li>
 *   <li>mismatched (不一致: 本地有 + 第三方有, 但金额/状态不一致)</li>
 *   <li>missing (本地缺失: 第三方有 + 本地无)</li>
 *   <li>extra (本地多余: 本地有 + 第三方无)</li>
 * </ul>
 */
@Getter
@Setter
@TableName("pay_reconciliation")
public class PayReconciliation extends BaseEntity {
    /** 对账日期 (yyyy-MM-dd) */
    private LocalDate reconDate;
    /** 对账渠道 */
    private String channel;
    /** 对账文件名 */
    private String fileName;
    /** 文件总笔数 (第三方侧) */
    private Integer totalCount;
    /** 文件总金额 (分) */
    private Long totalAmount;
    /** 匹配成功笔数 */
    private Integer matchedCount;
    /** 匹配成功金额 (分) */
    private Long matchedAmount;
    /** 不一致笔数 */
    private Integer mismatchedCount;
    /** 不一致金额 (分) */
    private Long mismatchedAmount;
    /** 本地缺失笔数 */
    private Integer missingCount;
    /** 本地缺失金额 (分) */
    private Long missingAmount;
    /** 本地多余笔数 */
    private Integer extraCount;
    /** 本地多余金额 (分) */
    private Long extraAmount;
    /** 对账状态 */
    private String status;
    /** 对账明细 JSON (数组) */
    private String details;
    /** 导入人 */
    private String importedBy;
    /** 导入时间 */
    private OffsetDateTime importedTime;
    /** 处理说明 */
    private String processMessage;

    /** status 常量 */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_MATCHED = "MATCHED";
    public static final String STATUS_MISMATCHED = "MISMATCHED";
    public static final String STATUS_IMPORTED = "IMPORTED";
    public static final String STATUS_FAILED = "FAILED";
}
