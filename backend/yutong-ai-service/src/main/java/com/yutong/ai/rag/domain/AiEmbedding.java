package com.yutong.ai.rag.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 向量嵌入记录。设计来源: 13-AI能力设计、57-完整DDL清单 ai_embedding
 * <p>
 * 注意: embedding 字段是 PostgreSQL vector(1536) 类型，MyBatis-Plus 不直接支持该类型映射。
 * 向量字段由原生 SQL 处理，不在 MyBatis-Plus 实体中映射。
 * 第一版仅记录 embedding_hash 用于去重，实际向量写入与检索由原生 SQL / pgvector 扩展完成。
 */
@Getter
@Setter
@TableName("ai_embedding")
public class AiEmbedding extends BaseEntity {

    /** 关联分块 ID */
    private String chunkId;

    /** 向量化模型标识 */
    private String embeddingModel;

    /** 向量维度 */
    private Integer embeddingDimension;

    /** 向量哈希，用于去重 */
    private String embeddingHash;

    /** 向量模态: text / image / video / audio (V061 多模态 Embedding，默认 text) */
    private String modality = "text";
}
