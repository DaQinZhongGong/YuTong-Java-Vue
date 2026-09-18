package com.yutong.ai.tool.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * MCP 市场实体。设计来源: V043 ai_mcp_market — P0 业界同类实现 AI 平价补齐：租户内 code 唯一，前端可视化配置 config_json 实时生效
 */
@Getter
@Setter
@TableName("ai_mcp_market")
public class AiMcpMarket extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_OFFLINE = "OFFLINE";
    public static final String STATUS_DISABLED = "DISABLED";

    /** 市场条目名称 */
    private String name;

    /** 市场编码，租户内唯一（软删过滤） */
    private String code;

    /** 描述 */
    private String description;

    /** 图标 URL */
    private String iconUrl;

    /** 提供方/作者 */
    private String provider;

    /** 分类（工具/数据源/业务等） */
    private String category;

    /** 评分 0.00-5.00 */
    private BigDecimal rating;

    /** 安装次数 */
    private Integer installCount;

    /** 状态: DRAFT/PUBLISHED/OFFLINE/DISABLED */
    private String status;

    /** 市场配置 JSON：含 mcp 配置、依赖、权限等，前端可视化实时生效，不落明文密钥 */
    @TableField("config_json")
    private String configJson;
}
