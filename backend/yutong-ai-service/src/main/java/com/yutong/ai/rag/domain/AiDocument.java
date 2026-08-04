package com.yutong.ai.rag.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * RAG 文档。设计来源: 13-AI能力设计、57-完整DDL清单 ai_document
 * 状态机: PENDING → PARSING → INDEXING → ACTIVE；失败转 FAILED；权限变更转 STALE。
 */
@Getter
@Setter
@TableName("ai_document")
public class AiDocument extends BaseEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_INDEXING = "INDEXING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_STALE = "STALE";

    public static final String SOURCE_DESIGN_DOC = "DESIGN_DOC";
    public static final String SOURCE_OPENAPI = "OPENAPI";
    public static final String SOURCE_DATA_DICT = "DATA_DICT";
    public static final String SOURCE_FAQ = "FAQ";

    /** 所属知识库 ID */
    private String kbId;

    /** 关联文件 ID（可选，受控入库可直接传 content） */
    private String fileId;

    /** 文档标题 */
    private String docTitle;

    /** 源类型: DESIGN_DOC / OPENAPI / DATA_DICT / FAQ */
    private String sourceType;

    /** 源地址 URI */
    private String sourceUri;

    /** 可见性: PRIVATE / TENANT / PUBLIC */
    private String visibility;

    /** 访问所需权限码 */
    private String permissionCode;

    /** 敏感等级: INTERNAL / CONFIDENTIAL / RESTRICTED */
    private String sensitivityLevel;

    /** 文档状态: PENDING / PARSING / INDEXING / ACTIVE / FAILED / STALE */
    private String documentStatus;

    /** 分块数量 */
    private Integer chunkCount;

    /** 失败/异常信息 */
    private String errorMessage;

    /** 索引完成时间 */
    private OffsetDateTime indexedTime;
}
