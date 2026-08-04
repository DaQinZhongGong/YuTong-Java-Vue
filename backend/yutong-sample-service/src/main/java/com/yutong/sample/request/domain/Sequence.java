package com.yutong.sample.request.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 业务编码序列。设计来源: 18-样例业务详细设计、57-完整DDL清单 sys_sequence
 * sequence_code='BIZ_REQUEST_NO' 用于申请单号生成，按 tenant_id + biz_date 隔离。
 * 采用 @Version 乐观锁更新，冲突重试最多 3 次。
 */
@Getter
@Setter
@TableName("sys_sequence")
public class Sequence extends BaseEntity {

    public static final String CODE_BIZ_REQUEST_NO = "BIZ_REQUEST_NO";
    public static final String RESET_POLICY_DAILY = "DAILY";

    private String sequenceCode;

    /** 业务日期 yyyyMMdd */
    private String bizDate;

    /** 当前已分配的最大值 */
    private Integer currentValue;

    private Integer step;

    /** 重置策略 DAILY */
    private String resetPolicy;
}
