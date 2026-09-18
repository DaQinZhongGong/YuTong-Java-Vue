package com.yutong.ai.skill.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * Skill 版本化实体。设计来源: V038 ai_skill
 * 约束: 租户内 skill_code + version_no 唯一; status DRAFT/PUBLISHED/DISABLED; skill_type docx/pdf/xlsx/custom。
 */
@Getter
@Setter
@TableName("ai_skill")
public class AiSkill extends BaseEntity {

    public static final String TYPE_DOCX = "docx";
    public static final String TYPE_PDF = "pdf";
    public static final String TYPE_XLSX = "xlsx";
    public static final String TYPE_CUSTOM = "custom";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_DISABLED = "DISABLED";

    /** Skill 编码，租户内 code+version 唯一 */
    private String skillCode;

    /** Skill 名称 */
    private String skillName;

    /** Skill 类型: docx/pdf/xlsx/custom */
    private String skillType;

    /** Skill 源路径 (仓库相对路径或对象存储 key) */
    private String sourcePath;

    /** SKILL.md 原文内容 */
    @TableField("skill_md")
    private String skillMd;

    /** 业务版本号 */
    private Integer versionNo;

    /** 状态: DRAFT/PUBLISHED/DISABLED */
    private String status;

    /** Skill 配置 JSON */
    @TableField("config_json")
    private String configJson;

    /** 脚本引用 — V043 P0 业界同类实现 平价补齐：脚本路径或对象存储 key，可空 */
    @TableField("script_ref")
    private String scriptRef;

    /** 关联市场 ID — V043 P0 业界同类实现 平价补齐：FK ai_mcp_market.id，可空表示自建 Skill */
    @TableField("market_id")
    private String marketId;
}
