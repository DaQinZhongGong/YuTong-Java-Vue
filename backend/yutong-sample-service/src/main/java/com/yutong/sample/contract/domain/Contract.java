package com.yutong.sample.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 合同主表。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 * 状态机: DRAFT→SUBMITTED→APPROVED→SIGNED→ARCHIVED，含 REJECTED 驳回分支和 CANCELLED 取消终态。
 * 验证能力: 文件版本管理、PG 全文检索、敏感字段脱敏、下载审计、归档只读。
 */
@Getter
@Setter
@TableName("contract")
public class Contract extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_SIGNED = "SIGNED";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String TYPE_GENERAL = "GENERAL";
    public static final String TYPE_SERVICE = "SERVICE";
    public static final String TYPE_PURCHASE = "PURCHASE";
    public static final String TYPE_SALE = "SALE";
    public static final String TYPE_LEASE = "LEASE";

    /** 合同号 CTyyyyMMddNNNN */
    private String contractNo;

    private String title;

    /** 合同类型 GENERAL/SERVICE/PURCHASE/SALE/LEASE */
    private String contractType;

    /** 甲方 */
    private String partyA;

    /** 乙方 */
    private String partyB;

    private LocalDate signedDate;

    private LocalDate effectiveDate;

    private LocalDate expireDate;

    /** 合同金额 */
    private BigDecimal amount;

    /** 币种 ISO 4217 */
    private String currency;

    /** 内容摘要 */
    private String contentSummary;

    /** 状态 */
    private String status;

    /** 当前版本号 */
    private Integer currentVersionNo;

    private String ownerUserId;

    private String ownerDeptId;

    private String ownerDeptPath;

    private OffsetDateTime submittedTime;

    private OffsetDateTime approvedTime;

    private OffsetDateTime signedTime;

    private OffsetDateTime archivedTime;

    /**
     * 全文检索向量（PG tsvector，由 SQL 维护，应用层不读不写）。
     * 该字段在 Java 实体中不存在，避免 MyBatis-Plus 类型映射失败 + 防止 tsvector 内容泄漏到前端。
     * 数据库列: search_vector (tsvector + GIN 索引)
     */
}
