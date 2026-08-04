package com.yutong.sample.contract.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 合同版本。设计来源: 35-样例业务矩阵扩展设计 P1 合同档案。
 * 每次合同内容变更创建一个不可变版本快照，关联 sys_file 文件 ID。
 */
@Getter
@Setter
@TableName("contract_version")
public class ContractVersion extends BaseEntity {

    private String contractId;

    /** 版本号 */
    private Integer versionNo;

    /** 关联 sys_file.id */
    private String fileId;

    private String fileNameSnapshot;

    private String fileChecksum;

    private Long fileSize;

    private String contentSummary;

    /** 变更说明 */
    private String changeLog;

    /** 是否当前版本 */
    private Boolean isCurrent;
}
