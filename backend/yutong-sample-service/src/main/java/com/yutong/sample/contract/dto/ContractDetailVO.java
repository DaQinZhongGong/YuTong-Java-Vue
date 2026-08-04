package com.yutong.sample.contract.dto;

import com.yutong.sample.contract.domain.ContractApproval;
import com.yutong.sample.contract.domain.ContractTag;
import com.yutong.sample.contract.domain.ContractVersion;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * 合同详情 VO（主表 + 版本列表 + 审批时间线 + 标签列表）。
 * 设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 *
 * <p>脱敏规则（对齐 67 号文档 DataScope 与 96 号文档字段权限）:
 * <ul>
 *   <li>amount: viewer 角色 → null（不可见合同金额）</li>
 *   <li>partyB: APPROVED 以下状态对 viewer 角色 → 部分脱敏</li>
 * </ul>
 */
@Data
public class ContractDetailVO {
    private String id;
    private String contractNo;
    private String title;
    private String contractType;
    private String partyA;
    private String partyB;
    private LocalDate signedDate;
    private LocalDate effectiveDate;
    private LocalDate expireDate;
    private BigDecimal amount;
    private String currency;
    private String contentSummary;
    private String status;
    private Integer currentVersionNo;
    private String ownerUserId;
    private OffsetDateTime submittedTime;
    private OffsetDateTime approvedTime;
    private OffsetDateTime signedTime;
    private OffsetDateTime archivedTime;
    private OffsetDateTime createdTime;
    private String createdBy;
    /** 版本列表（按版本号升序） */
    private List<ContractVersion> versions;
    /** 审批时间线（按时间升序） */
    private List<ContractApproval> approvals;
    /** 标签列表 */
    private List<ContractTag> tags;
    /** 是否可编辑（ARCHIVED/CANCELLED 不可编辑） */
    private Boolean editable;
}
