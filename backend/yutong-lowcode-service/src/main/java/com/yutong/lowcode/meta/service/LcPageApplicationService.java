package com.yutong.lowcode.meta.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.lowcode.meta.domain.LcAction;
import com.yutong.lowcode.meta.domain.LcComponent;
import com.yutong.lowcode.meta.domain.LcPage;
import com.yutong.lowcode.meta.dto.LcPageDetailVO;
import com.yutong.lowcode.meta.dto.SaveLcPageRequest;
import com.yutong.lowcode.meta.mapper.LcActionMapper;
import com.yutong.lowcode.meta.mapper.LcComponentMapper;
import com.yutong.lowcode.meta.mapper.LcPageMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 低代码页面应用服务。设计来源: 14-低代码平台设计、52-后端服务分工
 * 事务编排: 草稿保存、发布（不可变快照）、回滚（历史版本复制为草稿）。
 */
@Service
public class LcPageApplicationService {

    public static final String RESOURCE_CODE = "lowcode:page";

    private final LcPageMapper pageMapper;
    private final LcComponentMapper componentMapper;
    private final LcActionMapper actionMapper;
    private final LcDomainService domainService;
    private final ConfigHashService configHashService;
    private final DataScopeResolver dataScopeResolver;

    public LcPageApplicationService(LcPageMapper pageMapper,
                                    LcComponentMapper componentMapper,
                                    LcActionMapper actionMapper,
                                    LcDomainService domainService,
                                    ConfigHashService configHashService,
                                    DataScopeResolver dataScopeResolver) {
        this.pageMapper = pageMapper;
        this.componentMapper = componentMapper;
        this.actionMapper = actionMapper;
        this.domainService = domainService;
        this.configHashService = configHashService;
        this.dataScopeResolver = dataScopeResolver;
    }

    public PageResult<LcPage> pagePages(PageRequest request, String pageCode, String pageName, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<LcPage> wrapper = new LambdaQueryWrapper<LcPage>()
                .eq(LcPage::getTenantId, CurrentUserContext.getTenantId())
                .like(pageCode != null && !pageCode.isBlank(), LcPage::getPageCode, pageCode)
                .like(pageName != null && !pageName.isBlank(), LcPage::getPageName, pageName)
                .eq(status != null && !status.isBlank(), LcPage::getStatus, status)
                .orderByDesc(LcPage::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<LcPage> page = pageMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    private void applyDataScope(LambdaQueryWrapper<LcPage> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(LcPage::getCreatedBy, userId);
    }

    public LcPageDetailVO getPageDetail(String id) {
        LcPage page = pageMapper.selectById(id);
        if (page == null) {
            throw new ResourceNotFoundException("页面不存在: " + id);
        }
        List<LcComponent> components = componentMapper.selectList(
                new LambdaQueryWrapper<LcComponent>()
                        .eq(LcComponent::getPageId, id)
                        .orderByAsc(LcComponent::getSortNo));
        List<LcAction> actions = actionMapper.selectList(
                new LambdaQueryWrapper<LcAction>()
                        .eq(LcAction::getPageId, id));

        LcPageDetailVO vo = new LcPageDetailVO();
        BeanUtils.copyProperties(page, vo);
        vo.setComponents(components.stream().map(this::toComponentDTO).toList());
        vo.setActions(actions.stream().map(this::toActionDTO).toList());
        return vo;
    }

    @Transactional
    public LcPage saveDraft(SaveLcPageRequest request) {
        if (request.getId() == null || request.getId().isBlank()) {
            LcPage entity = new LcPage();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(CurrentUserContext.getTenantId());
            entity.setCreatedBy(CurrentUserContext.getUserId());
            entity.setPageCode(request.getPageCode());
            entity.setPageName(request.getPageName());
            entity.setEntityId(request.getEntityId());
            entity.setPageType(request.getPageType());
            entity.setLayoutJson(jsonOrNull(request.getLayoutJson()));
            entity.setLayoutSchemaVersion(
                    request.getLayoutSchemaVersion() != null ? request.getLayoutSchemaVersion() : "1.0");
            entity.setVersionNo(1);
            entity.setStatus(LcPage.STATUS_DRAFT);
            pageMapper.insert(entity);
            saveComponents(entity.getId(), request.getComponents());
            saveActions(entity.getId(), request.getActions());
            return entity;
        }

        LcPage existing = pageMapper.selectById(request.getId());
        if (existing == null) {
            throw new ResourceNotFoundException("页面不存在: " + request.getId());
        }
        checkVersion(request.getVersion(), existing.getVersion());
        if (!LcPage.STATUS_DRAFT.equals(existing.getStatus())) {
            throw new BusinessConflictException(
                    "页面当前状态[" + existing.getStatus() + "]不允许编辑，仅 DRAFT 可编辑");
        }
        existing.setPageName(request.getPageName());
        existing.setEntityId(request.getEntityId());
        existing.setPageType(request.getPageType());
        existing.setLayoutJson(jsonOrNull(request.getLayoutJson()));
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = pageMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        saveComponents(existing.getId(), request.getComponents());
        saveActions(existing.getId(), request.getActions());
        return existing;
    }

    /**
     * 发布: DRAFT → PUBLISHED。生成不可变 config_hash，versionNo 自增，记录 published_time。
     */
    @Transactional
    public LcPage publish(String id, Integer version) {
        LcPage page = pageMapper.selectById(id);
        if (page == null) {
            throw new ResourceNotFoundException("页面不存在: " + id);
        }
        checkVersion(version, page.getVersion());
        if (LcPage.STATUS_PUBLISHED.equals(page.getStatus())) {
            return page;
        }
        domainService.validatePageTransition(page.getStatus(), "PUBLISH");

        List<LcComponent> components = componentMapper.selectList(
                new LambdaQueryWrapper<LcComponent>().eq(LcComponent::getPageId, id));
        List<LcAction> actions = actionMapper.selectList(
                new LambdaQueryWrapper<LcAction>().eq(LcAction::getPageId, id));
        // 计算页面配置 hash 用于审计与版本一致性校验。
        // 设计来源: 14-低代码平台设计 版本与发布；当前 lc_page 表未存储 config_hash 字段，
        // 仅 entity 表有 config_hash 列。第一版页面以 version_no + layout_schema_version 标识版本。
        configHashService.computePageHash(
                page.getPageCode(), page.getPageType(), page.getLayoutJson(), components, actions);
        page.setVersionNo(page.getVersionNo() + 1);
        page.setStatus(LcPage.STATUS_PUBLISHED);
        page.setPublishedTime(OffsetDateTime.now());
        page.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = pageMapper.updateById(page);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return page;
    }

    /**
     * 回滚: PUBLISHED → DRAFT。本质是把当前发布版本复制为新草稿后重新编辑发布。
     * 第一版实现: 将状态改回 DRAFT，保留版本号，开发者可修改后重新发布。
     */
    @Transactional
    public LcPage rollback(String id, Integer version) {
        LcPage page = pageMapper.selectById(id);
        if (page == null) {
            throw new ResourceNotFoundException("页面不存在: " + id);
        }
        checkVersion(version, page.getVersion());
        domainService.validatePageTransition(page.getStatus(), "ROLLBACK");
        page.setStatus(LcPage.STATUS_DRAFT);
        page.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = pageMapper.updateById(page);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return page;
    }

    @Transactional
    public void delete(String id) {
        LcPage page = pageMapper.selectById(id);
        if (page == null) {
            throw new ResourceNotFoundException("页面不存在: " + id);
        }
        // BaseEntity 已配置 @TableLogic，deleteById 自动逻辑删除
        pageMapper.deleteById(id);
    }

    private void saveComponents(String pageId, List<SaveLcPageRequest.LcComponentDTO> components) {
        componentMapper.delete(new LambdaQueryWrapper<LcComponent>().eq(LcComponent::getPageId, pageId));
        if (components == null || components.isEmpty()) {
            return;
        }
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        int index = 1;
        for (SaveLcPageRequest.LcComponentDTO dto : components) {
            LcComponent entity = new LcComponent();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCreatedBy(userId);
            entity.setPageId(pageId);
            entity.setComponentCode(dto.getComponentCode());
            entity.setComponentType(dto.getComponentType());
            // GA2-28: jsonb 列拒绝空字符串, 需将空值转 null
            entity.setPropsJson(jsonOrNull(dto.getPropsJson()));
            entity.setRulesJson(jsonOrNull(dto.getRulesJson()));
            entity.setEventsJson(jsonOrNull(dto.getEventsJson()));
            entity.setPropsSchemaVersion(
                    dto.getPropsSchemaVersion() != null ? dto.getPropsSchemaVersion() : "1.0");
            entity.setParentComponentId(dto.getParentComponentId());
            entity.setSortNo(dto.getSortNo() != null ? dto.getSortNo() : index);
            componentMapper.insert(entity);
            index++;
        }
    }

    private void saveActions(String pageId, List<SaveLcPageRequest.LcActionDTO> actions) {
        actionMapper.delete(new LambdaQueryWrapper<LcAction>().eq(LcAction::getPageId, pageId));
        if (actions == null || actions.isEmpty()) {
            return;
        }
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        for (SaveLcPageRequest.LcActionDTO dto : actions) {
            LcAction entity = new LcAction();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCreatedBy(userId);
            entity.setPageId(pageId);
            entity.setActionCode(dto.getActionCode());
            // action_name NOT NULL, 缺省回退到 actionCode 保证完整性
            String actionName = dto.getActionName();
            entity.setActionName(actionName == null || actionName.isBlank() ? dto.getActionCode() : actionName);
            entity.setActionType(dto.getActionType());
            entity.setPermissionCode(dto.getPermissionCode());
            entity.setConfirmRequired(dto.getConfirmRequired());
            entity.setApiMethod(dto.getApiMethod());
            entity.setApiPath(dto.getApiPath());
            // GA2-28: payload_mapping 也可能为 jsonb, 空字符串转 null 防止 PostgreSQL jsonb 解析失败
            entity.setPayloadMapping(jsonOrNull(dto.getPayloadMapping()));
            actionMapper.insert(entity);
        }
    }

    /**
     * GA2-28: PostgreSQL jsonb 列拒绝空字符串 ("invalid input syntax for type json")。
     * 将空白字符串转为 null, 仅当存在有效 JSON 内容时才写入。
     */
    private static String jsonOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        return s;
    }

    private LcPageDetailVO.ComponentDTO toComponentDTO(LcComponent c) {
        LcPageDetailVO.ComponentDTO dto = new LcPageDetailVO.ComponentDTO();
        BeanUtils.copyProperties(c, dto);
        return dto;
    }

    private LcPageDetailVO.ActionDTO toActionDTO(LcAction a) {
        LcPageDetailVO.ActionDTO dto = new LcPageDetailVO.ActionDTO();
        BeanUtils.copyProperties(a, dto);
        return dto;
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
