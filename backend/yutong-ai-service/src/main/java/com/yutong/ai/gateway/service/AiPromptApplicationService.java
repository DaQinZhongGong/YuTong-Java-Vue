package com.yutong.ai.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.gateway.domain.AiPromptTemplate;
import com.yutong.ai.gateway.mapper.AiPromptTemplateMapper;
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
 * Prompt 模板应用服务。设计来源: 13-AI能力设计、52-后端服务分工
 * 事务编排: 草稿保存、发布（DRAFT → PUBLISHED，versionNo 自增）。
 */
@Service
public class AiPromptApplicationService {

    public static final String RESOURCE_CODE = "ai:prompt";

    private final AiPromptTemplateMapper templateMapper;
    private final DataScopeResolver dataScopeResolver;

    public AiPromptApplicationService(AiPromptTemplateMapper templateMapper, DataScopeResolver dataScopeResolver) {
        this.templateMapper = templateMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 分页查询 Prompt 模板。
     *
     * @param templateCode 模板编码（模糊匹配），可为空
     * @param scenario     使用场景，可为空
     * @param status       状态: DRAFT / PUBLISHED，可为空
     */
    public PageResult<AiPromptTemplate> pageTemplates(PageRequest request, String templateCode, String scenario, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiPromptTemplate> wrapper = new LambdaQueryWrapper<AiPromptTemplate>()
                .eq(AiPromptTemplate::getTenantId, CurrentUserContext.getTenantId())
                .like(templateCode != null && !templateCode.isBlank(), AiPromptTemplate::getTemplateCode, templateCode)
                .eq(scenario != null && !scenario.isBlank(), AiPromptTemplate::getScenario, scenario)
                .eq(status != null && !status.isBlank(), AiPromptTemplate::getStatus, status)
                .orderByDesc(AiPromptTemplate::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<AiPromptTemplate> page = templateMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    private void applyDataScope(LambdaQueryWrapper<AiPromptTemplate> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(AiPromptTemplate::getCreatedBy, userId);
    }

    /**
     * 查询模板详情。不存在抛 ResourceNotFoundException。
     */
    public AiPromptTemplate getTemplate(String id) {
        AiPromptTemplate template = templateMapper.selectById(id);
        if (template == null) {
            throw new ResourceNotFoundException("Prompt 模板不存在: " + id);
        }
        return template;
    }

    /**
     * 新建或修改模板草稿（status=DRAFT）。
     * <ul>
     *   <li>id 为空 → 新建，status=DRAFT，versionNo=1</li>
     *   <li>id 非空 → 修改，仅 DRAFT 状态可编辑，校验 version 乐观锁</li>
     * </ul>
     */
    @Transactional
    public AiPromptTemplate saveDraft(AiPromptTemplate template) {
        if (template.getId() == null || template.getId().isBlank()) {
            // 新建
            template.setId(IdGenerator.nextId());
            template.setTenantId(CurrentUserContext.getTenantId());
            template.setCreatedBy(CurrentUserContext.getUserId());
            template.setVersionNo(template.getVersionNo() != null ? template.getVersionNo() : 1);
            template.setStatus(AiPromptTemplate.STATUS_DRAFT);
            templateMapper.insert(template);
            return template;
        }
        // 修改
        AiPromptTemplate existing = getTemplate(template.getId());
        checkVersion(template.getVersion(), existing.getVersion());
        if (!AiPromptTemplate.STATUS_DRAFT.equals(existing.getStatus())) {
            throw new BusinessConflictException(
                    "模板当前状态[" + existing.getStatus() + "]不允许编辑，仅 DRAFT 可编辑");
        }
        template.setTenantId(existing.getTenantId());
        template.setStatus(AiPromptTemplate.STATUS_DRAFT);
        template.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = templateMapper.updateById(template);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return template;
    }

    /**
     * 发布模板: DRAFT → PUBLISHED，versionNo 自增。
     * 已 PUBLISHED 的模板幂等返回。
     */
    @Transactional
    public AiPromptTemplate publish(String id, Integer version) {
        AiPromptTemplate template = getTemplate(id);
        checkVersion(version, template.getVersion());
        // 幂等: 已发布直接返回
        if (AiPromptTemplate.STATUS_PUBLISHED.equals(template.getStatus())) {
            return template;
        }
        if (!AiPromptTemplate.STATUS_DRAFT.equals(template.getStatus())) {
            throw new BusinessConflictException(
                    "模板当前状态[" + template.getStatus() + "]不允许发布，仅 DRAFT 可发布");
        }
        template.setVersionNo(template.getVersionNo() + 1);
        template.setStatus(AiPromptTemplate.STATUS_PUBLISHED);
        template.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = templateMapper.updateById(template);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return template;
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
