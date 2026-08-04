package com.yutong.lowcode.meta.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageResult;
import com.yutong.lowcode.meta.domain.LcComponentRegistry;
import com.yutong.lowcode.meta.dto.ComponentRegistryPageQuery;
import com.yutong.lowcode.meta.dto.SaveComponentRegistryRequest;
import com.yutong.lowcode.meta.mapper.LcComponentRegistryMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 低代码组件协议元数据应用服务。设计来源: 36-低代码高级能力设计 组件协议（GA2-L191 子任务 B）
 * 事务编排: 新建草稿、更新（乐观锁）、发布（DRAFT→PUBLISHED）、禁用（PUBLISHED→DISABLED）、逻辑删除。
 * 设计器组件库通过 listPublished 拉取已发布组件清单。
 */
@Service
public class ComponentRegistryApplicationService {

    private final LcComponentRegistryMapper registryMapper;

    public ComponentRegistryApplicationService(LcComponentRegistryMapper registryMapper) {
        this.registryMapper = registryMapper;
    }

    /**
     * 分页查询，支持 category/platform/status/keyword 过滤。
     * keyword 模糊匹配 component_code 或 component_name。
     */
    public PageResult<LcComponentRegistry> page(ComponentRegistryPageQuery query) {
        LambdaQueryWrapper<LcComponentRegistry> wrapper = new LambdaQueryWrapper<LcComponentRegistry>()
                .eq(LcComponentRegistry::getTenantId, CurrentUserContext.getTenantId())
                .eq(query.getCategory() != null && !query.getCategory().isBlank(),
                        LcComponentRegistry::getCategory, query.getCategory())
                .eq(query.getPlatform() != null && !query.getPlatform().isBlank(),
                        LcComponentRegistry::getPlatform, query.getPlatform())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        LcComponentRegistry::getStatus, query.getStatus())
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        w -> w.like(LcComponentRegistry::getComponentCode, query.getKeyword())
                                .or().like(LcComponentRegistry::getComponentName, query.getKeyword()))
                .orderByAsc(LcComponentRegistry::getCategory)
                .orderByAsc(LcComponentRegistry::getSortNo);
        Page<LcComponentRegistry> page = registryMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPageNo(), query.getPageSize());
    }

    /** 详情。不存在抛 ResourceNotFoundException。 */
    public LcComponentRegistry get(String id) {
        LcComponentRegistry entity = registryMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("组件协议不存在: " + id);
        }
        return entity;
    }

    /** 按 component_code 查询，返回 null 表示不存在。 */
    public LcComponentRegistry getByCode(String code) {
        return registryMapper.selectOne(
                new LambdaQueryWrapper<LcComponentRegistry>()
                        .eq(LcComponentRegistry::getTenantId, CurrentUserContext.getTenantId())
                        .eq(LcComponentRegistry::getComponentCode, code));
    }

    /** 按分类列表，按 sort_no 升序。 */
    public List<LcComponentRegistry> listByCategory(String category) {
        return registryMapper.selectList(
                new LambdaQueryWrapper<LcComponentRegistry>()
                        .eq(LcComponentRegistry::getTenantId, CurrentUserContext.getTenantId())
                        .eq(LcComponentRegistry::getCategory, category)
                        .orderByAsc(LcComponentRegistry::getSortNo));
    }

    /** 已发布列表，设计器组件库渲染物料区用，按 category、sort_no 排序。 */
    public List<LcComponentRegistry> listPublished() {
        return registryMapper.selectList(
                new LambdaQueryWrapper<LcComponentRegistry>()
                        .eq(LcComponentRegistry::getTenantId, CurrentUserContext.getTenantId())
                        .eq(LcComponentRegistry::getStatus, LcComponentRegistry.STATUS_PUBLISHED)
                        .eq(LcComponentRegistry::getDeprecated, false)
                        .orderByAsc(LcComponentRegistry::getCategory)
                        .orderByAsc(LcComponentRegistry::getSortNo));
    }

    /** 新建组件协议元数据，默认状态 DRAFT。code 重复抛 BusinessException。 */
    @Transactional
    public LcComponentRegistry create(SaveComponentRegistryRequest req) {
        checkCodeDuplicate(req.getComponentCode(), null);
        LcComponentRegistry entity = new LcComponentRegistry();
        BeanUtils.copyProperties(req, entity, "id", "version");
        entity.setId(IdGenerator.nextId());
        entity.setTenantId(CurrentUserContext.getTenantId());
        entity.setCreatedBy(CurrentUserContext.getUserId());
        // 默认值
        if (entity.getPlatform() == null) {
            entity.setPlatform(LcComponentRegistry.PLATFORM_BOTH);
        }
        if (entity.getCompatibilityGrade() == null) {
            entity.setCompatibilityGrade(LcComponentRegistry.GRADE_STABLE);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(LcComponentRegistry.STATUS_DRAFT);
        }
        if (entity.getComponentVersion() == null) {
            entity.setComponentVersion("1.0.0");
        }
        if (entity.getSortNo() == null) {
            entity.setSortNo(0);
        }
        if (entity.getDeprecated() == null) {
            entity.setDeprecated(false);
        }
        if (entity.getPermissionSupport() == null) {
            entity.setPermissionSupport(false);
        }
        if (entity.getValidationSupport() == null) {
            entity.setValidationSupport(false);
        }
        registryMapper.insert(entity);
        return entity;
    }

    /** 更新组件协议元数据（乐观锁）。状态不通过 update 修改，需走 publish/disable。 */
    @Transactional
    public LcComponentRegistry update(String id, SaveComponentRegistryRequest req) {
        LcComponentRegistry existing = registryMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("组件协议不存在: " + id);
        }
        checkVersion(req.getVersion(), existing.getVersion());
        checkCodeDuplicate(req.getComponentCode(), id);
        // status 不通过 update 修改，排除 id/version/status
        BeanUtils.copyProperties(req, existing, "id", "version", "status");
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = registryMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return existing;
    }

    /** 逻辑删除。 */
    @Transactional
    public void delete(String id) {
        LcComponentRegistry existing = registryMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("组件协议不存在: " + id);
        }
        int affected = registryMapper.deleteById(id);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
    }

    /** 发布: DRAFT → PUBLISHED。已 PUBLISHED 幂等返回。 */
    @Transactional
    public void publish(String id) {
        LcComponentRegistry existing = registryMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("组件协议不存在: " + id);
        }
        if (LcComponentRegistry.STATUS_PUBLISHED.equals(existing.getStatus())) {
            return;
        }
        if (!LcComponentRegistry.STATUS_DRAFT.equals(existing.getStatus())) {
            throw new BusinessConflictException(
                    "组件当前状态[" + existing.getStatus() + "]不允许发布，仅 DRAFT 可发布");
        }
        existing.setStatus(LcComponentRegistry.STATUS_PUBLISHED);
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = registryMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
    }

    /** 禁用: PUBLISHED → DISABLED。已 DISABLED 幂等返回。 */
    @Transactional
    public void disable(String id) {
        LcComponentRegistry existing = registryMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("组件协议不存在: " + id);
        }
        if (LcComponentRegistry.STATUS_DISABLED.equals(existing.getStatus())) {
            return;
        }
        if (!LcComponentRegistry.STATUS_PUBLISHED.equals(existing.getStatus())) {
            throw new BusinessConflictException(
                    "组件当前状态[" + existing.getStatus() + "]不允许禁用，仅 PUBLISHED 可禁用");
        }
        existing.setStatus(LcComponentRegistry.STATUS_DISABLED);
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = registryMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
    }

    /** code 重复校验，excludeId 用于更新时排除自身。 */
    private void checkCodeDuplicate(String componentCode, String excludeId) {
        LambdaQueryWrapper<LcComponentRegistry> wrapper = new LambdaQueryWrapper<LcComponentRegistry>()
                .eq(LcComponentRegistry::getTenantId, CurrentUserContext.getTenantId())
                .eq(LcComponentRegistry::getComponentCode, componentCode);
        if (excludeId != null && !excludeId.isBlank()) {
            wrapper.ne(LcComponentRegistry::getId, excludeId);
        }
        Long count = registryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(
                    ErrorCode.LC_ENTITY_CODE_DUPLICATE,
                    "组件编码已存在: " + componentCode);
        }
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
