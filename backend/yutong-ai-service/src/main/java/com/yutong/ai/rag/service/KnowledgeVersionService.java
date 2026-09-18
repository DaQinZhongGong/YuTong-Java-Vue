package com.yutong.ai.rag.service;

import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.domain.AiKnowledgeVersion;
import com.yutong.ai.rag.mapper.AiKnowledgeBaseMapper;
import com.yutong.ai.rag.mapper.AiKnowledgeVersionMapper;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 知识库版本管理 — 创建/激活/归档
 * 设计来源: ADR 0004 P2-E 知识库 RAG 深度
 *
 * 落点: 39-知识库运营详设
 *
 * 工作流:
 *   1. createVersion(knowledgeId) → 新增 BUILDING 记录, version = max+1
 *   2. 业务方执行文档重新装载 (异步), 完成后调 activate
 *   3. activate(knowledgeId, version) → 旧 ACTIVE → ARCHIVED, 新 → ACTIVE
 *      同时更新 ai_knowledge_base.current_version
 *
 * 简化实现: 本版本只做元数据管理 (version 编号 + 状态切换)
 *           真正的文档快照/差异对比留作下轮 (需要 chunk 表加 version 字段)
 */
@Service
public class KnowledgeVersionService {

    private final AiKnowledgeBaseMapper kbMapper;
    private final AiKnowledgeVersionMapper versionMapper;

    public KnowledgeVersionService(AiKnowledgeBaseMapper kbMapper, AiKnowledgeVersionMapper versionMapper) {
        this.kbMapper = kbMapper;
        this.versionMapper = versionMapper;
    }

    /**
     * 创建新版本
     * @return 新版本号 (>= 1)
     */
    @Transactional
    public int createVersion(String knowledgeId, String note) {
        AiKnowledgeBase kb = kbMapper.selectById(knowledgeId);
        if (kb == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "知识库不存在: " + knowledgeId);
        }
        // 计算下一个 version
        Integer maxVersion = versionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiKnowledgeVersion>()
                        .eq("knowledge_id", knowledgeId)
                        .orderByDesc("version")
                        .last("LIMIT 1")
        ).stream().findFirst().map(AiKnowledgeVersion::getVersion).orElse(0);

        AiKnowledgeVersion v = new AiKnowledgeVersion();
        v.setKnowledgeId(knowledgeId);
        v.setVersion(maxVersion + 1);
        v.setStatus(AiKnowledgeVersion.STATUS_BUILDING);
        v.setDocCount(0);
        v.setChunkCount(0);
        v.setCreatedBy(currentUserId());
        v.setCreatedAt(LocalDateTime.now());
        v.setNote(note);
        versionMapper.insert(v);
        return v.getVersion();
    }

    /**
     * 激活指定版本 (旧 ACTIVE → ARCHIVED)
     * 同时更新 kb.current_version
     */
    @Transactional
    public void activate(String knowledgeId, int version) {
        AiKnowledgeVersion target = findVersion(knowledgeId, version);
        if (AiKnowledgeVersion.STATUS_ARCHIVED.equals(target.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "已归档版本不能激活: v" + version);
        }
        // 1. 旧 ACTIVE → ARCHIVED
        List<AiKnowledgeVersion> active = versionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiKnowledgeVersion>()
                        .eq("knowledge_id", knowledgeId)
                        .eq("status", AiKnowledgeVersion.STATUS_ACTIVE)
        );
        for (AiKnowledgeVersion v : active) {
            v.setStatus(AiKnowledgeVersion.STATUS_ARCHIVED);
            versionMapper.updateById(v);
        }
        // 2. 目标 → ACTIVE
        target.setStatus(AiKnowledgeVersion.STATUS_ACTIVE);
        target.setActivatedAt(LocalDateTime.now());
        versionMapper.updateById(target);
        // 3. 同步 kb.currentVersion
        AiKnowledgeBase kb = kbMapper.selectById(knowledgeId);
        if (kb != null) {
            kb.setCurrentVersion(version);
            kbMapper.updateById(kb);
        }
    }

    /**
     * 列出所有版本 (倒序: 最新在前)
     */
    public List<AiKnowledgeVersion> list(String knowledgeId) {
        return versionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiKnowledgeVersion>()
                        .eq("knowledge_id", knowledgeId)
                        .orderByDesc("version")
        );
    }

    /**
     * 查单个版本
     */
    public AiKnowledgeVersion findVersion(String knowledgeId, int version) {
        AiKnowledgeVersion v = versionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<AiKnowledgeVersion>()
                        .eq("knowledge_id", knowledgeId)
                        .eq("version", version)
                        .last("LIMIT 1")
        );
        if (v == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "版本不存在: knowledgeId=" + knowledgeId + " version=" + version);
        }
        return v;
    }

    /** 标记版本为 FAILED (异步装载失败时调用) */
    public void markFailed(String knowledgeId, int version) {
        AiKnowledgeVersion v = findVersion(knowledgeId, version);
        v.setStatus(AiKnowledgeVersion.STATUS_FAILED);
        versionMapper.updateById(v);
    }

    private String currentUserId() {
        try {
            String id = CurrentUserContext.getUserId();
            return id == null || id.isBlank() ? "system" : id;
        } catch (Exception e) {
            return "system";
        }
    }
}
