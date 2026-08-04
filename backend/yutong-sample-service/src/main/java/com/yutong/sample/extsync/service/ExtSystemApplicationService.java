package com.yutong.sample.extsync.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.sample.extsync.domain.ExtSystem;
import com.yutong.sample.extsync.dto.SaveExtSystemRequest;
import com.yutong.sample.extsync.mapper.ExtSystemMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 外部系统应用服务。设计来源: 35-样例业务矩阵扩展设计 P2 外部接口同步。
 *
 * <p>提供外部系统的 CRUD 操作, 编码租户内唯一。
 */
@Service
public class ExtSystemApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ExtSystemApplicationService.class);

    /** 外部系统资源编码，对齐 permissions.yaml biz:ext-sync:system:* 命名。 */
    public static final String RESOURCE_CODE = "biz:ext-sync:system";

    private final ExtSystemMapper systemMapper;
    private final DataScopeResolver dataScopeResolver;

    public ExtSystemApplicationService(ExtSystemMapper systemMapper, DataScopeResolver dataScopeResolver) {
        this.systemMapper = systemMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    public Page<ExtSystem> pageSystems(int pageNo, int pageSize, String systemCode, String systemName, String status) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        Page<ExtSystem> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<ExtSystem> wrapper = new LambdaQueryWrapper<ExtSystem>()
                .eq(ExtSystem::getTenantId, CurrentUserContext.getTenantId())
                .orderByDesc(ExtSystem::getCreatedTime);
        if (systemCode != null && !systemCode.isBlank()) {
            wrapper.eq(ExtSystem::getSystemCode, systemCode);
        }
        if (systemName != null && !systemName.isBlank()) {
            wrapper.like(ExtSystem::getSystemName, systemName);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(ExtSystem::getStatus, status);
        }
        applyDataScope(wrapper, scope);
        return systemMapper.selectPage(page, wrapper);
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * ExtSystem 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF/DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<ExtSystem> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(ExtSystem::getCreatedBy, userId);
    }

    public ExtSystem getSystem(String id) {
        ExtSystem system = systemMapper.selectById(id);
        if (system == null) {
            throw new ResourceNotFoundException(ErrorCode.EXT_SYSTEM_NOT_FOUND);
        }
        return system;
    }

    public ExtSystem getSystemByCode(String systemCode) {
        return systemMapper.selectOne(new LambdaQueryWrapper<ExtSystem>()
                .eq(ExtSystem::getSystemCode, systemCode));
    }

    public List<ExtSystem> listAll() {
        return systemMapper.selectList(new LambdaQueryWrapper<ExtSystem>()
                .eq(ExtSystem::getStatus, ExtSystem.STATUS_ACTIVE)
                .orderByDesc(ExtSystem::getCreatedTime));
    }

    @Transactional
    public ExtSystem createSystem(SaveExtSystemRequest request) {
        ExtSystem existing = getSystemByCode(request.getSystemCode());
        if (existing != null) {
            throw new BusinessException(ErrorCode.EXT_SYSTEM_CODE_DUPLICATE, "外部系统编码已存在: " + request.getSystemCode());
        }
        ExtSystem system = new ExtSystem();
        system.setId(IdGenerator.nextId());
        system.setSystemCode(request.getSystemCode());
        system.setSystemName(request.getSystemName());
        system.setDescription(request.getDescription());
        system.setEndpoint(request.getEndpoint());
        system.setAuthType(request.getAuthType() == null ? ExtSystem.AUTH_HMAC_SHA256 : request.getAuthType());
        system.setCredentials(request.getCredentials());
        system.setConnectTimeout(request.getConnectTimeout() == null ? 5 : request.getConnectTimeout());
        system.setReadTimeout(request.getReadTimeout() == null ? 15 : request.getReadTimeout());
        system.setMaxRetryCount(request.getMaxRetryCount() == null ? 3 : request.getMaxRetryCount());
        system.setRetryBackoffMs(request.getRetryBackoffMs() == null ? 1000 : request.getRetryBackoffMs());
        system.setStatus(request.getStatus() == null ? ExtSystem.STATUS_ACTIVE : request.getStatus());
        try {
            systemMapper.insert(system);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.EXT_SYSTEM_CODE_DUPLICATE, "外部系统编码已存在: " + request.getSystemCode());
        }
        log.info("createSystem: code={} name={}", system.getSystemCode(), system.getSystemName());
        return system;
    }

    @Transactional
    public ExtSystem updateSystem(String id, SaveExtSystemRequest request) {
        ExtSystem system = getSystem(id);
        system.setSystemName(request.getSystemName());
        system.setDescription(request.getDescription());
        system.setEndpoint(request.getEndpoint());
        system.setAuthType(request.getAuthType());
        system.setCredentials(request.getCredentials());
        system.setConnectTimeout(request.getConnectTimeout());
        system.setReadTimeout(request.getReadTimeout());
        system.setMaxRetryCount(request.getMaxRetryCount());
        system.setRetryBackoffMs(request.getRetryBackoffMs());
        if (request.getStatus() != null) {
            system.setStatus(request.getStatus());
        }
        systemMapper.updateById(system);
        return system;
    }

    @Transactional
    public void deleteSystem(String id) {
        ExtSystem system = getSystem(id);
        systemMapper.deleteById(system.getId());
        log.info("deleteSystem: id={} code={}", id, system.getSystemCode());
    }
}
