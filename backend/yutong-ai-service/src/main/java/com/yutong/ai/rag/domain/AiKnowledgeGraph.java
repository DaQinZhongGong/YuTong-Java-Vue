package com.yutong.ai.rag.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识图谱。设计来源: V043 ai_knowledge_graph — P0 平价能力
 * 状态: DRAFT → BUILDING → READY / FAILED；graph_json 存储图谱快照
 */
@Getter
@Setter
@TableName("ai_knowledge_graph")
public class AiKnowledgeGraph extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_BUILDING = "BUILDING";
    public static final String STATUS_READY = "READY";
    public static final String STATUS_FAILED = "FAILED";

    /** 关联知识库 ID (ai_knowledge_base.id)，可空表示独立图谱 */
    private String kbId;

    /** 图谱名称 */
    private String name;

    /** 构建状态: DRAFT/BUILDING/READY/FAILED */
    private String status;

    /** 图谱 JSON：{nodes:[{id,label,type,props}], edges:[{source,target,relation}]} */
    @TableField("graph_json")
    private String graphJson;

    /** 实体数量 */
    private Integer entityCount;

    /** 关系数量 */
    private Integer relationCount;
}
