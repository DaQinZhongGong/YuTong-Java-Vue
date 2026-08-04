package com.yutong.sample.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 合同标签。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 */
@Getter
@Setter
@TableName("contract_tag")
public class ContractTag extends BaseEntity {

    private String contractId;

    private String tagName;
}
