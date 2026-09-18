package com.yutong.ai.rag.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 知识库版本 — 记录每次"重新解析/装载"的快照
 * 设计来源: ADR 0004 P2-E 知识库 RAG 深度
 *
 * 落点: 39-知识库运营详设 + 57-完整 DDL 清单
 *
 * 工作原理:
 *   - 每次 kbInfo 重新解析文档产生新版本
 *   - 旧版本只读,新版本可激活
 *   - 检索默认走 currentVersion 指向的版本
 *
 * 状态机:
 *   BUILDING → ACTIVE (默认) → ARCHIVED
 *   ACTIVE → ARCHIVED (用户主动归档旧版本)
 */
@Getter
@Setter
@TableName("ai_knowledge_version")
public class AiKnowledgeVersion {

    public static final String STATUS_BUILDING = "BUILDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_ARCHIVED = "ARCHIVED";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 所属知识库 ID (varchar 32 ULID) */
    @TableField("knowledge_id")
    private String knowledgeId;

    /** 版本号 (从 1 开始, 同 kb 内单调递增) */
    @TableField("version")
    private Integer version;

    /** 状态: BUILDING/ACTIVE/ARCHIVED/FAILED */
    @TableField("status")
    private String status;

    /** 该版本下文档数量 (冗余统计, 避免每次 count) */
    @TableField("doc_count")
    private Integer docCount;

    /** 该版本下 chunk 数量 (冗余统计) */
    @TableField("chunk_count")
    private Integer chunkCount;

    /** 创建人 userId */
    @TableField("created_by")
    private String createdBy;

    /** 创建时间 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 激活时间 (status 变 ACTIVE 时填) */
    @TableField("activated_at")
    private LocalDateTime activatedAt;

    /** 版本说明 (本次装载的变更点 / 来源) */
    @TableField("note")
    private String note;
}
