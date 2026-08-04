package com.yutong.lowcode.plugin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageResult;
import com.yutong.lowcode.plugin.domain.CodeTemplate;
import com.yutong.lowcode.plugin.dto.CodeTemplatePageQuery;
import com.yutong.lowcode.plugin.dto.SaveCodeTemplateRequest;
import com.yutong.lowcode.plugin.mapper.CodeTemplateMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 代码生成模板应用服务（45 号文档「插件与模板生态设计」E0）。
 * 编排层：分页查询、详情、创建、更新、删除。
 */
@Service
public class CodeTemplateApplicationService {

    private final CodeTemplateMapper codeTemplateMapper;

    public CodeTemplateApplicationService(CodeTemplateMapper codeTemplateMapper) {
        this.codeTemplateMapper = codeTemplateMapper;
    }

    /**
     * 分页查询代码模板，支持 keyword/category/engineType/status 过滤
     */
    public PageResult<CodeTemplate> page(CodeTemplatePageQuery query) {
        LambdaQueryWrapper<CodeTemplate> wrapper = new LambdaQueryWrapper<CodeTemplate>()
                .eq(CodeTemplate::getTenantId, CurrentUserContext.getTenantId())
                .eq(query.getCategory() != null && !query.getCategory().isBlank(),
                        CodeTemplate::getCategory, query.getCategory())
                .eq(query.getEngineType() != null && !query.getEngineType().isBlank(),
                        CodeTemplate::getEngineType, query.getEngineType())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        CodeTemplate::getStatus, query.getStatus())
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        w -> w.like(CodeTemplate::getTemplateCode, query.getKeyword())
                                .or().like(CodeTemplate::getTemplateName, query.getKeyword()))
                .orderByDesc(CodeTemplate::getCreatedTime);
        Page<CodeTemplate> page = codeTemplateMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPageNo(), query.getPageSize());
    }

    /**
     * 代码模板详情
     */
    public CodeTemplate get(String id) {
        CodeTemplate entity = codeTemplateMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("模板不存在: " + id);
        }
        return entity;
    }

    /**
     * 创建代码模板，默认状态 ACTIVE
     */
    @Transactional
    public CodeTemplate create(SaveCodeTemplateRequest req) {
        checkCodeDuplicate(req.getTemplateCode(), null);
        CodeTemplate entity = new CodeTemplate();
        BeanUtils.copyProperties(req, entity, "id", "version");
        entity.setId(IdGenerator.nextId());
        entity.setTenantId(CurrentUserContext.getTenantId());
        entity.setCreatedBy(CurrentUserContext.getUserId());
        if (entity.getEngineType() == null) {
            entity.setEngineType(CodeTemplate.ENGINE_FREEMARKER);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(CodeTemplate.STATUS_ACTIVE);
        }
        codeTemplateMapper.insert(entity);
        return entity;
    }

    /**
     * 更新代码模板（乐观锁）
     */
    @Transactional
    public CodeTemplate update(String id, SaveCodeTemplateRequest req) {
        CodeTemplate existing = codeTemplateMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("模板不存在: " + id);
        }
        checkVersion(req.getVersion(), existing.getVersion());
        checkCodeDuplicate(req.getTemplateCode(), id);
        BeanUtils.copyProperties(req, existing, "id", "version");
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = codeTemplateMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return existing;
    }

    /**
     * 删除代码模板（逻辑删除）
     */
    @Transactional
    public void delete(String id) {
        CodeTemplate existing = codeTemplateMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("模板不存在: " + id);
        }
        int affected = codeTemplateMapper.deleteById(id);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
    }

    /**
     * 编码重复校验
     */
    private void checkCodeDuplicate(String templateCode, String excludeId) {
        LambdaQueryWrapper<CodeTemplate> wrapper = new LambdaQueryWrapper<CodeTemplate>()
                .eq(CodeTemplate::getTenantId, CurrentUserContext.getTenantId())
                .eq(CodeTemplate::getTemplateCode, templateCode);
        if (excludeId != null && !excludeId.isBlank()) {
            wrapper.ne(CodeTemplate::getId, excludeId);
        }
        Long count = codeTemplateMapper.selectCount(wrapper);
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
