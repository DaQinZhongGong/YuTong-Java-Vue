package com.yutong.lowcode.plugin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageResult;
import com.yutong.lowcode.plugin.domain.MktTemplate;
import com.yutong.lowcode.plugin.dto.TemplatePageQuery;
import com.yutong.lowcode.plugin.mapper.MktTemplateMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模板市场应用服务（45 号文档「插件与模板生态设计」）。
 * 编排层：分页查询、详情、创建、更新、删除、发布。
 */
@Service
public class TemplateApplicationService {

    private final MktTemplateMapper mktTemplateMapper;

    public TemplateApplicationService(MktTemplateMapper mktTemplateMapper) {
        this.mktTemplateMapper = mktTemplateMapper;
    }

    /**
     * 分页查询模板，支持 keyword/category/status 过滤
     */
    public PageResult<MktTemplate> page(TemplatePageQuery query) {
        LambdaQueryWrapper<MktTemplate> wrapper = new LambdaQueryWrapper<MktTemplate>()
                .eq(MktTemplate::getTenantId, CurrentUserContext.getTenantId())
                .eq(query.getCategory() != null && !query.getCategory().isBlank(),
                        MktTemplate::getCategory, query.getCategory())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        MktTemplate::getStatus, query.getStatus())
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        w -> w.like(MktTemplate::getTemplateCode, query.getKeyword())
                                .or().like(MktTemplate::getTemplateName, query.getKeyword()))
                .orderByDesc(MktTemplate::getCreatedTime);
        Page<MktTemplate> page = mktTemplateMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPageNo(), query.getPageSize());
    }

    /**
     * 模板详情
     */
    public MktTemplate get(String id) {
        MktTemplate entity = mktTemplateMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("模板不存在: " + id);
        }
        return entity;
    }

    /**
     * 创建模板，默认状态 DRAFT
     */
    @Transactional
    public MktTemplate create(MktTemplate req) {
        checkCodeDuplicate(req.getTemplateCode(), null);
        MktTemplate entity = new MktTemplate();
        BeanUtils.copyProperties(req, entity, "id", "version");
        entity.setId(IdGenerator.nextId());
        entity.setTenantId(CurrentUserContext.getTenantId());
        entity.setCreatedBy(CurrentUserContext.getUserId());
        // 默认值
        if (entity.getStatus() == null) {
            entity.setStatus(MktTemplate.STATUS_DRAFT);
        }
        if (entity.getInstallCount() == null) {
            entity.setInstallCount(0);
        }
        mktTemplateMapper.insert(entity);
        return entity;
    }

    /**
     * 更新模板（乐观锁）
     */
    @Transactional
    public MktTemplate update(String id, MktTemplate req) {
        MktTemplate existing = mktTemplateMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("模板不存在: " + id);
        }
        checkVersion(req.getVersion(), existing.getVersion());
        checkCodeDuplicate(req.getTemplateCode(), id);
        BeanUtils.copyProperties(req, existing, "id", "version", "installCount");
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = mktTemplateMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return existing;
    }

    /**
     * 删除模板（逻辑删除）
     */
    @Transactional
    public void delete(String id) {
        MktTemplate existing = mktTemplateMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("模板不存在: " + id);
        }
        int affected = mktTemplateMapper.deleteById(id);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
    }

    /**
     * 发布模板: DRAFT → PUBLISHED
     */
    @Transactional
    public void publish(String id) {
        MktTemplate existing = mktTemplateMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("模板不存在: " + id);
        }
        if (MktTemplate.STATUS_PUBLISHED.equals(existing.getStatus())) {
            return;
        }
        if (!MktTemplate.STATUS_DRAFT.equals(existing.getStatus())) {
            throw new BusinessConflictException(
                    "模板当前状态[" + existing.getStatus() + "]不允许发布，仅 DRAFT 可发布");
        }
        existing.setStatus(MktTemplate.STATUS_PUBLISHED);
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = mktTemplateMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
    }

    /**
     * 编码重复校验
     */
    private void checkCodeDuplicate(String templateCode, String excludeId) {
        LambdaQueryWrapper<MktTemplate> wrapper = new LambdaQueryWrapper<MktTemplate>()
                .eq(MktTemplate::getTenantId, CurrentUserContext.getTenantId())
                .eq(MktTemplate::getTemplateCode, templateCode);
        if (excludeId != null && !excludeId.isBlank()) {
            wrapper.ne(MktTemplate::getId, excludeId);
        }
        Long count = mktTemplateMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessConflictException("模板编码已存在: " + templateCode);
        }
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
