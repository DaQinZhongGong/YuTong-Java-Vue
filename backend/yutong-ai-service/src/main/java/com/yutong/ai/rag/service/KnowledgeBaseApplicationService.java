package com.yutong.ai.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.rag.domain.AiKnowledgeBase;
import com.yutong.ai.rag.mapper.AiKnowledgeBaseMapper;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 知识库应用服务。设计来源: 13-AI能力设计、52-后端服务分工
 * 事务编排: 草稿保存、发布（DRAFT → ACTIVE）、禁用（ACTIVE/DRAFT → DISABLED）。
 * 修改校验 version 乐观锁。
 */
@Service
public class KnowledgeBaseApplicationService {

    public static final String RESOURCE_CODE = "ai:knowledge-base";

    private final AiKnowledgeBaseMapper kbMapper;
    private final DataScopeResolver dataScopeResolver;

    public KnowledgeBaseApplicationService(AiKnowledgeBaseMapper kbMapper, DataScopeResolver dataScopeResolver) {
        this.kbMapper = kbMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 分页查询知识库。
     *
     * @param kbCode 知识库编码（模糊匹配），可为空
     * @param status 状态: DRAFT / ACTIVE / DISABLED，可为空
     */
    public PageResult<AiKnowledgeBase> pageKnowledgeBases(PageRequest request, String kbCode, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiKnowledgeBase> wrapper = new LambdaQueryWrapper<AiKnowledgeBase>()
                .eq(AiKnowledgeBase::getTenantId, CurrentUserContext.getTenantId())
                .like(kbCode != null && !kbCode.isBlank(), AiKnowledgeBase::getKbCode, kbCode)
                .eq(status != null && !status.isBlank(), AiKnowledgeBase::getStatus, status)
                .orderByDesc(AiKnowledgeBase::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<AiKnowledgeBase> page = kbMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    private void applyDataScope(LambdaQueryWrapper<AiKnowledgeBase> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(AiKnowledgeBase::getOwnerUserId, userId);
    }

    /**
     * 查询知识库详情。不存在抛 ResourceNotFoundException。
     */
    public AiKnowledgeBase getKnowledgeBase(String id) {
        AiKnowledgeBase kb = kbMapper.selectById(id);
        if (kb == null) {
            throw new ResourceNotFoundException("知识库不存在: " + id);
        }
        return kb;
    }

    /**
     * 新建或修改知识库草稿（status=DRAFT）。
     * <ul>
     *   <li>id 为空 → 新建，status=DRAFT，ownerUserId 缺省取当前用户</li>
     *   <li>id 非空 → 修改，仅 DRAFT 状态可编辑，校验 version 乐观锁</li>
     * </ul>
     */
    @Transactional
    public AiKnowledgeBase saveDraft(AiKnowledgeBase kb) {
        if (kb.getId() == null || kb.getId().isBlank()) {
            // 新建
            kb.setId(IdGenerator.nextId());
            kb.setTenantId(CurrentUserContext.getTenantId());
            kb.setCreatedBy(CurrentUserContext.getUserId());
            kb.setStatus(AiKnowledgeBase.STATUS_DRAFT);
            if (kb.getOwnerUserId() == null || kb.getOwnerUserId().isBlank()) {
                kb.setOwnerUserId(CurrentUserContext.getUserId());
            }
            kbMapper.insert(kb);
            return kb;
        }
        // 修改
        AiKnowledgeBase existing = getKnowledgeBase(kb.getId());
        checkVersion(kb.getVersion(), existing.getVersion());
        if (!AiKnowledgeBase.STATUS_DRAFT.equals(existing.getStatus())) {
            throw new BusinessConflictException(
                    "知识库当前状态[" + existing.getStatus() + "]不允许编辑，仅 DRAFT 可编辑");
        }
        kb.setTenantId(existing.getTenantId());
        kb.setStatus(AiKnowledgeBase.STATUS_DRAFT);
        kb.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = kbMapper.updateById(kb);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return kb;
    }

    /**
     * 发布知识库: DRAFT → ACTIVE。
     * 已 ACTIVE 的知识库幂等返回。
     */
    @Transactional
    public AiKnowledgeBase publish(String id, Integer version) {
        AiKnowledgeBase kb = getKnowledgeBase(id);
        checkVersion(version, kb.getVersion());
        // 幂等: 已发布直接返回
        if (AiKnowledgeBase.STATUS_ACTIVE.equals(kb.getStatus())) {
            return kb;
        }
        if (!AiKnowledgeBase.STATUS_DRAFT.equals(kb.getStatus())) {
            throw new BusinessConflictException(
                    "知识库当前状态[" + kb.getStatus() + "]不允许发布，仅 DRAFT 可发布");
        }
        kb.setStatus(AiKnowledgeBase.STATUS_ACTIVE);
        kb.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = kbMapper.updateById(kb);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return kb;
    }

    /**
     * 禁用知识库: ACTIVE/DRAFT → DISABLED。
     * 已 DISABLED 的知识库幂等返回。
     */
    @Transactional
    public AiKnowledgeBase disable(String id, Integer version) {
        AiKnowledgeBase kb = getKnowledgeBase(id);
        checkVersion(version, kb.getVersion());
        // 幂等: 已禁用直接返回
        if (AiKnowledgeBase.STATUS_DISABLED.equals(kb.getStatus())) {
            return kb;
        }
        if (!AiKnowledgeBase.STATUS_ACTIVE.equals(kb.getStatus())
                && !AiKnowledgeBase.STATUS_DRAFT.equals(kb.getStatus())) {
            throw new BusinessConflictException(
                    "知识库当前状态[" + kb.getStatus() + "]不允许禁用，仅 ACTIVE/DRAFT 可禁用");
        }
        kb.setStatus(AiKnowledgeBase.STATUS_DISABLED);
        kb.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = kbMapper.updateById(kb);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return kb;
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
