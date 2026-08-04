package com.yutong.ai.rag.domain;

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
}
