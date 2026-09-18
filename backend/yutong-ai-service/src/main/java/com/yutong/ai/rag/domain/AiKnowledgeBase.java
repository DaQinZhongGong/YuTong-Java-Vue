package com.yutong.ai.rag.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * RAG 知识库。设计来源: 13-AI能力设计、57-完整DDL清单 ai_knowledge_base
 * 状态: DRAFT → ACTIVE → DISABLED；发布后方可入库文档。
 */
@Getter
@Setter
@TableName("ai_knowledge_base")
public class AiKnowledgeBase extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";

    public static final String VISIBILITY_PRIVATE = "PRIVATE";
    public static final String VISIBILITY_TENANT = "TENANT";
    public static final String VISIBILITY_PUBLIC = "PUBLIC";

    public static final String SENSITIVITY_INTERNAL = "INTERNAL";
    public static final String SENSITIVITY_CONFIDENTIAL = "CONFIDENTIAL";
    public static final String SENSITIVITY_RESTRICTED = "RESTRICTED";

    /** 知识库编码，租户内唯一 */
    private String kbCode;

    /** 知识库名称 */
    private String kbName;

    /** 描述说明 */
    private String description;

    /** ACL 策略 JSON */
    private String aclPolicyJson;

    /** 向量化模型标识 */
    private String embeddingModel;

    /** 可见性: PRIVATE / TENANT / PUBLIC */
    private String visibility;

    /** 访问所需权限码 */
    private String permissionCode;

    /** 敏感等级: INTERNAL / CONFIDENTIAL / RESTRICTED */
    private String sensitivityLevel;

    /** 状态: DRAFT / ACTIVE / DISABLED */
    private String status;

    /** 所有者用户 ID */
    private String ownerUserId;

    // ===== V037 RAG parity 扩展 =====

    /** 切分参数 JSON: {separator, blockSize, overlap, retrieveLimit, similarityThreshold} */
    @TableField("chunk_params_json")
    private String chunkParamsJson;

    /** 装载器类型: pdf/word/excel/csv/md/txt/json/code/folder/github */
    @TableField("loader_type")
    private String loaderType;

    /** 是否启用重排 */
    @TableField("reranker_enabled")
    private Boolean rerankerEnabled;

    /** 重排供应商: alibailian/siliconflow/zhipu */
    @TableField("reranker_provider")
    private String rerankerProvider;

    /** 是否启用混合检索 (pgvector + tsvector 加权) */
    @TableField("hybrid_enabled")
    private Boolean hybridEnabled;

    // ===== V050 P2-E 混合检索可配 =====

    /** 混合检索向量权重 0~1 (null = 默认 0.70) */
    @TableField("hybrid_vector_weight")
    private java.math.BigDecimal hybridVectorWeight;

    /** 混合检索返回数量 1~50 (null = 默认 5) */
    @TableField("hybrid_top_k")
    private Integer hybridTopK;

    /** 混合分数准入门限 0~1 (null = 默认 0.01) */
    @TableField("hybrid_min_score")
    private java.math.BigDecimal hybridMinScore;

    // ===== P2-E 知识库版本管理 (2026-09-03) =====

    /** 当前激活的版本号 (与 ai_knowledge_version.version 对应, 0 表示未发布过版本) */
    @TableField("current_version")
    private Integer currentVersion;

    // ===== V061 多模态 Embedding =====

    /** 期望向量维度，默认 1536；多模态/百炼模型可配置实际维度 (1~8192) */
    @TableField("embedding_dimension")
    private Integer embeddingDimension;
}
