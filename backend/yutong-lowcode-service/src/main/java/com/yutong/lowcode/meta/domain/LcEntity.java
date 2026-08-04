package com.yutong.lowcode.meta.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 低代码实体（元模型入口）。设计来源: 14-低代码平台设计、57-完整DDL清单 lc_entity
 * 状态: DRAFT → PUBLISHED → DISABLED；发布时生成不可变版本快照 + config_hash。
 */
@Getter
@Setter
@TableName("lc_entity")
public class LcEntity extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 实体编码，租户内唯一 */
    private String entityCode;

    private String entityName;

    /** 目标表名，生成 DDL 时使用 */
    private String tableName;

    private String moduleCode;

    /** 元模型版本号，每次发布自增 */
    private Integer versionNo;

    /** 元模型 schema 版本，平台升级时迁移依据 */
    private String schemaVersion;

    /** 配置摘要（字段+关系 hash），发布时计算，用于 diff */
    private String configHash;

    /** DRAFT / PUBLISHED / DISABLED */
    private String status;

    private String ownerUserId;
}
