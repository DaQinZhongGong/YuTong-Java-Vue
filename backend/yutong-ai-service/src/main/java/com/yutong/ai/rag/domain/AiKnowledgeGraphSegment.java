package com.yutong.ai.rag.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 知识图谱分段。设计来源: V043 ai_knowledge_graph_segment
 */
@Getter
@Setter
@TableName("ai_knowledge_graph_segment")
public class AiKnowledgeGraphSegment extends BaseEntity {

    /** 所属图谱 ID (ai_knowledge_graph.id) */
    private String graphId;

    /** 来源分块 ID (ai_document_chunk.id)，varchar32 ULID */
    private String sourceChunkId;

    /** 实体 JSON：[{id, label, type, props}] */
    @TableField("entity_json")
    private String entityJson;

    /** 关系 JSON：[{source, target, relation, props}] */
    @TableField("relation_json")
    private String relationJson;
}
