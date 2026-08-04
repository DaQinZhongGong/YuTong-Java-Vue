package com.yutong.system.file.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/** 文件业务绑定。设计来源: 57-完整DDL清单 biz_file_rel */
@Getter
@Setter
@TableName("biz_file_rel")
public class BizFileRel extends BaseEntity {

    /** 业务类型 (如 customer / product) */
    private String bizType;

    /** 业务对象 ID */
    private String bizId;

    /** 关联的文件 ID (sys_file.id) */
    private String fileId;

    /** 关系类型 (如 AVATAR / ATTACHMENT / CONTRACT) */
    private String relType;

    /** 排序号 */
    private Integer sortNo;
}
