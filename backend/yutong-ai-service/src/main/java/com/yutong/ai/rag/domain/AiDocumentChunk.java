package com.yutong.ai.rag.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.yutong.infra.persistence.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 文档分块。设计来源: 13-AI能力设计、57-完整DDL清单 ai_document_chunk
 * 约束: chunk_hash 用于去重；chunk_text 为 PostgreSQL text 类型。
 */
@Getter
@Setter
@TableName("ai_document_chunk")
public class AiDocumentChunk extends BaseEntity {

    /** 所属知识库 ID */
    private String knowledgeBaseId;

    /** 所属文档 ID */
    private String documentId;

    /** 分块序号，从 1 开始 */
    private Integer chunkNo;

    /** 分块文本 */
    private String chunkText;

    /** 分块哈希（SHA-256），用于去重 */
    private String chunkHash;

    /** Token 数量估算 */
    private Integer tokenCount;

    /** 章节路径，如 "3.2.1 接口设计" */
    private String sectionPath;

    /** 访问所需权限码 */
    private String permissionCode;

    /** 数据范围 */
    private String dataScope;

    /** 敏感等级: INTERNAL / CONFIDENTIAL / RESTRICTED */
    private String sensitivityLevel;

    /** ACL 标签 JSON */
    private String aclTagsJson;

    // ===== P2-E 知识库 RAG 深度 (2026-09-03) =====

    /**
     * 起始字符偏移 (基于原始文档纯文本, 0-based, char 索引)
     * 用于前端高亮回溯, 引用 RAG 命中片段在原文位置
     */
    @com.baomidou.mybatisplus.annotation.TableField("start_offset")
    private Integer startOffset;

    /**
     * 结束字符偏移 (基于原始文档纯文本, 0-based, char 索引, 不含)
     * 与 startOffset 配合使用: chunkText = docText.substring(startOffset, endOffset)
     */
    @com.baomidou.mybatisplus.annotation.TableField("end_offset")
    private Integer endOffset;
}
