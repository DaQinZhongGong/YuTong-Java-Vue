package com.yutong.infra.persistence;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 所有业务表实体的基类。
 * 设计来源: 05-数据架构设计、98-后端实现蓝图与代码骨架详设
 * 约束: 主键 IdType.INPUT (字符串 ULID)；逻辑删除 + 乐观锁 + 租户隔离。
 *
 * <p>GA2-15 修复: 为 tenantId/createdBy/createdTime/updatedBy/updatedTime/deleted/version
 * 添加 {@link TableField}(fill=...) 注解，使 {@link YutongMetaObjectHandler#insertFill} 的
 * strictInsertFill 能自动填充这些字段。修复前 strictInsertFill 对未标注 fill 的字段不生效，
 * 导致 SysConfig/DictType/DictItem 等 create 接口因 tenant_id NOT NULL 约束失败 (500)。
 *
 * <p>id 字段保持 {@link IdType#INPUT}，由各 service 显式调用 {@code IdGenerator.nextId()}
 * 生成 ULID (参照 ProductService/CustomerService/OperationLogService 等范式)。
 * strictInsertFill 不覆盖已有值，已有显式 setId 的 service 不受影响。
 */
@Getter
@Setter
public abstract class BaseEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    /** 租户 ID, 由 MetaObjectHandler 在 insert 时自动填充为 CurrentUserContext.getTenantId() */
    @TableField(fill = FieldFill.INSERT)
    private String tenantId;

    /** 创建人, 由 MetaObjectHandler 在 insert 时自动填充为 CurrentUserContext.getUserId() */
    @TableField(fill = FieldFill.INSERT)
    private String createdBy;

    /** 创建时间, 由 MetaObjectHandler 在 insert 时自动填充为 OffsetDateTime.now() */
    @TableField(fill = FieldFill.INSERT)
    private OffsetDateTime createdTime;

    /** 更新人, 由 MetaObjectHandler 在 insert/update 时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;

    /** 更新时间, 由 MetaObjectHandler 在 insert/update 时自动填充 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private OffsetDateTime updatedTime;

    /** 逻辑删除标志, 由 MetaObjectHandler 在 insert 时自动填充为 false */
    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Boolean deleted;

    /** 乐观锁版本号, 由 MetaObjectHandler 在 insert 时自动填充为 0 */
    @Version
    @TableField(fill = FieldFill.INSERT)
    private Integer version;

    private String remark;
}
