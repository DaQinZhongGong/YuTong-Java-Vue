package com.yutong.sample.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 合同附件。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 * 复用 sys_file 文件元数据，contract_attachment 仅维护合同与文件的关联关系。
 */
@Getter
@Setter
@TableName("contract_attachment")
public class ContractAttachment extends BaseEntity {

    private String contractId;

    /** 关联 sys_file.id */
    private String fileId;

    private String fileNameSnapshot;

    /** 附件类型 GENERAL/SUPPLEMENT/ANNEX/EVIDENCE */
    private String attachmentType;

    private Integer sortNo;
}
