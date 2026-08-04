package com.yutong.sample.contract.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 新建/更新合同请求。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 */
@Data
public class SaveContractRequest {

    @NotBlank(message = "标题不能为空")
    @Size(max = 256, message = "标题不能超过 256 字")
    private String title;

    @Size(max = 32, message = "合同类型不能超过 32 字")
    private String contractType;

    @NotBlank(message = "甲方不能为空")
    @Size(max = 128, message = "甲方不能超过 128 字")
    private String partyA;

    @NotBlank(message = "乙方不能为空")
    @Size(max = 128, message = "乙方不能超过 128 字")
    private String partyB;

    private LocalDate signedDate;

    private LocalDate effectiveDate;

    private LocalDate expireDate;

    private BigDecimal amount;

    @Size(max = 8, message = "币种不能超过 8 字")
    private String currency;

    @Size(max = 2000, message = "内容摘要不能超过 2000 字")
    private String contentSummary;
}
