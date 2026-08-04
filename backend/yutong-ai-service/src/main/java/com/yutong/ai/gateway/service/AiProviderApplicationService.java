package com.yutong.ai.gateway.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.ai.gateway.domain.AiProvider;
import com.yutong.ai.gateway.mapper.AiProviderMapper;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 供应商应用服务。设计来源: 13-AI能力设计、52-后端服务分工
 * 事务编排: 草稿保存、启用、禁用。乐观锁校验 version。
 */
@Service
public class AiProviderApplicationService {

    public static final String RESOURCE_CODE = "ai:provider";

    private final AiProviderMapper providerMapper;
    private final DataScopeResolver dataScopeResolver;

    public AiProviderApplicationService(AiProviderMapper providerMapper, DataScopeResolver dataScopeResolver) {
        this.providerMapper = providerMapper;
        this.dataScopeResolver = dataScopeResolver;
    }

    /**
     * 分页查询供应商。
     *
     * @param providerCode 供应商编码（模糊匹配），可为空
     * @param status       启用状态: ENABLED / DISABLED，可为空；当 enabled 非空时此参数被忽略
     * @param keyword      关键字（模糊匹配 provider_code 或 provider_name），可为空
     * @param enabled      是否启用筛选（Boolean），可为空；非空时覆盖 status
     */
    public PageResult<AiProvider> pageProviders(PageRequest request, String providerCode, String status,
                                                String keyword, Boolean enabled) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<AiProvider> wrapper = new LambdaQueryWrapper<AiProvider>()
                .eq(AiProvider::getTenantId, CurrentUserContext.getTenantId())
                .like(providerCode != null && !providerCode.isBlank(), AiProvider::getProviderCode, providerCode)
                .and(keyword != null && !keyword.isBlank(),
                        w -> w.like(AiProvider::getProviderCode, keyword)
                                .or()
                                .like(AiProvider::getProviderName, keyword))
                .eq(enabled != null, AiProvider::getEnabled, enabled)
                .eq(enabled == null && "ENABLED".equals(status), AiProvider::getEnabled, true)
                .eq(enabled == null && "DISABLED".equals(status), AiProvider::getEnabled, false)
                .orderByAsc(AiProvider::getPriority)
                .orderByDesc(AiProvider::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<AiProvider> page = providerMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    private void applyDataScope(LambdaQueryWrapper<AiProvider> wrapper, DataScope scope) {
        if (scope == null) return;
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) return;
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(AiProvider::getCreatedBy, userId);
    }

    /**
     * 查询供应商详情。不存在抛 ResourceNotFoundException。
     */
    public AiProvider getProvider(String id) {
        AiProvider provider = providerMapper.selectById(id);
        if (provider == null) {
            throw new ResourceNotFoundException("AI 供应商不存在: " + id);
        }
        return provider;
    }

    /**
     * 新建或修改供应商草稿。
     * <ul>
     *   <li>id 为空 → 新建，生成 ULID 主键</li>
     *   <li>id 非空 → 修改，校验 version 乐观锁</li>
     * </ul>
     */
    @Transactional
    public AiProvider saveDraft(AiProvider provider) {
        // endpoint NOT NULL 约束前置校验，避免 DataIntegrityViolation 暴露给调用方
        if (provider.getEndpoint() == null || provider.getEndpoint().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "供应商 endpoint 不能为空");
        }
        if (provider.getProviderCode() == null || provider.getProviderCode().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "供应商 providerCode 不能为空");
        }
        if (provider.getProviderName() == null || provider.getProviderName().isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "供应商 providerName 不能为空");
        }
        if (provider.getId() == null || provider.getId().isBlank()) {
            // 新建
            provider.setId(IdGenerator.nextId());
            provider.setTenantId(CurrentUserContext.getTenantId());
            provider.setCreatedBy(CurrentUserContext.getUserId());
            providerMapper.insert(provider);
            return provider;
        }
        // 修改
        AiProvider existing = getProvider(provider.getId());
        checkVersion(provider.getVersion(), existing.getVersion());
        provider.setTenantId(existing.getTenantId());
        provider.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = providerMapper.updateById(provider);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return provider;
    }

    /**
     * 启用供应商。
     */
    @Transactional
    public AiProvider enable(String id, Integer version) {
        AiProvider provider = getProvider(id);
        checkVersion(version, provider.getVersion());
        provider.setEnabled(true);
        provider.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = providerMapper.updateById(provider);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return provider;
    }

    /**
     * 禁用供应商。
     */
    @Transactional
    public AiProvider disable(String id, Integer version) {
        AiProvider provider = getProvider(id);
        checkVersion(version, provider.getVersion());
        provider.setEnabled(false);
        provider.setUpdatedBy(CurrentUserContext.getUserId());
        int rows = providerMapper.updateById(provider);
        if (rows == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return provider;
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }
}
