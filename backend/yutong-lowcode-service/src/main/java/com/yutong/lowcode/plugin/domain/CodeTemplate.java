package com.yutong.lowcode.plugin.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 代码生成模板实体（45 号文档「插件与模板生态设计」E0）。
 * 保存代码模板元数据、分类、标签、模板内容和引擎类型。
 * 引擎: FREEMARKER / VELOCITY
 * 状态: ACTIVE / INACTIVE
 */
@Getter
@Setter
@TableName("code_template")
public class CodeTemplate extends BaseEntity {

    // ===== engine_type 模板引擎 =====
    public static final String ENGINE_FREEMARKER = "FREEMARKER";
    public static final String ENGINE_VELOCITY = "VELOCITY";

    // ===== status 生命周期 =====
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_INACTIVE = "INACTIVE";

    /** 模板编码，租户内唯一 */
    private String templateCode;

    private String templateName;

    /** 模板分类 ENTITY/CONTROLLER/SERVICE/MAPPER/VUE_LIST/VUE_FORM/DDL 等 */
    private String category;

    /** 标签（逗号分隔） */
    private String tags;

    /** FREEMARKER / VELOCITY */
    private String engineType;

    /** 模板内容 */
    private String content;

    /** 模板描述 */
    private String description;

    /** ACTIVE / INACTIVE */
    private String status;
}
