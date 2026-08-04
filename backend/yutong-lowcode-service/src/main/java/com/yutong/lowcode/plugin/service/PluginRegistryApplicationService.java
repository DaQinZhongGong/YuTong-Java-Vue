package com.yutong.lowcode.plugin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageResult;
import com.yutong.lowcode.plugin.domain.PluginRegistry;
import com.yutong.lowcode.plugin.dto.PluginRegistryPageQuery;
import com.yutong.lowcode.plugin.dto.SavePluginRegistryRequest;
import com.yutong.lowcode.plugin.mapper.PluginRegistryMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 插件注册表应用服务（45 号文档「插件与模板生态设计」E0）。
 * 编排层：分页查询、详情、创建、更新、删除。
 */
@Service
public class PluginRegistryApplicationService {

    private final PluginRegistryMapper pluginRegistryMapper;

    public PluginRegistryApplicationService(PluginRegistryMapper pluginRegistryMapper) {
        this.pluginRegistryMapper = pluginRegistryMapper;
    }

    /**
     * 分页查询插件注册表，支持 keyword/pluginType/status 过滤
     */
    public PageResult<PluginRegistry> page(PluginRegistryPageQuery query) {
        LambdaQueryWrapper<PluginRegistry> wrapper = new LambdaQueryWrapper<PluginRegistry>()
                .eq(PluginRegistry::getTenantId, CurrentUserContext.getTenantId())
                .eq(query.getPluginType() != null && !query.getPluginType().isBlank(),
                        PluginRegistry::getPluginType, query.getPluginType())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        PluginRegistry::getStatus, query.getStatus())
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        w -> w.like(PluginRegistry::getPluginCode, query.getKeyword())
                                .or().like(PluginRegistry::getPluginName, query.getKeyword()))
                .orderByDesc(PluginRegistry::getCreatedTime);
        Page<PluginRegistry> page = pluginRegistryMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPageNo(), query.getPageSize());
    }

    /**
     * 插件注册表详情
     */
    public PluginRegistry get(String id) {
        PluginRegistry entity = pluginRegistryMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("插件不存在: " + id);
        }
        return entity;
    }

    /**
     * 创建插件注册表，默认状态 ACTIVE
     */
    @Transactional
    public PluginRegistry create(SavePluginRegistryRequest req) {
        checkCodeDuplicate(req.getPluginCode(), null);
        PluginRegistry entity = new PluginRegistry();
        BeanUtils.copyProperties(req, entity, "id", "version");
        entity.setId(IdGenerator.nextId());
        entity.setTenantId(CurrentUserContext.getTenantId());
        entity.setCreatedBy(CurrentUserContext.getUserId());
        if (entity.getPluginVersion() == null) {
            entity.setPluginVersion("1.0.0");
        }
        if (entity.getStatus() == null) {
            entity.setStatus(PluginRegistry.STATUS_ACTIVE);
        }
        pluginRegistryMapper.insert(entity);
        return entity;
    }

    /**
     * 更新插件注册表（乐观锁）
     */
    @Transactional
    public PluginRegistry update(String id, SavePluginRegistryRequest req) {
        PluginRegistry existing = pluginRegistryMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("插件不存在: " + id);
        }
        checkVersion(req.getVersion(), existing.getVersion());
        checkCodeDuplicate(req.getPluginCode(), id);
        BeanUtils.copyProperties(req, existing, "id", "version");
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = pluginRegistryMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return existing;
    }

    /**
     * 删除插件注册表（逻辑删除）
     */
    @Transactional
    public void delete(String id) {
        PluginRegistry existing = pluginRegistryMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("插件不存在: " + id);
        }
        int affected = pluginRegistryMapper.deleteById(id);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
    }

    /**
     * 编码重复校验
     */
    private void checkCodeDuplicate(String pluginCode, String excludeId) {
        LambdaQueryWrapper<PluginRegistry> wrapper = new LambdaQueryWrapper<PluginRegistry>()
                .eq(PluginRegistry::getTenantId, CurrentUserContext.getTenantId())
                .eq(PluginRegistry::getPluginCode, pluginCode);
        if (excludeId != null && !excludeId.isBlank()) {
            wrapper.ne(PluginRegistry::getId, excludeId);
        }
        Long count = pluginRegistryMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessConflictException("插件编码已存在: " + pluginCode);
        }
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
