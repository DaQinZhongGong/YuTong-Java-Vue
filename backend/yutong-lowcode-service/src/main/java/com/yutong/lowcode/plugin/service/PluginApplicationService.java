package com.yutong.lowcode.plugin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessConflictException;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageResult;
import com.yutong.lowcode.plugin.domain.PluginAuditLog;
import com.yutong.lowcode.plugin.domain.PluginInstallation;
import com.yutong.lowcode.plugin.domain.PluginPackage;
import com.yutong.lowcode.plugin.dto.InstallPluginRequest;
import com.yutong.lowcode.plugin.dto.MarketPluginVO;
import com.yutong.lowcode.plugin.dto.PluginDependencyCheckResult;
import com.yutong.lowcode.plugin.dto.PluginPackagePageQuery;
import com.yutong.lowcode.plugin.dto.SavePluginPackageRequest;
import com.yutong.lowcode.plugin.mapper.PluginAuditLogMapper;
import com.yutong.lowcode.plugin.mapper.PluginInstallationMapper;
import com.yutong.lowcode.plugin.mapper.PluginPackageMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 插件应用服务（45 号文档「插件与模板生态设计」）。
 * 编排层：分页查询、安装、卸载、启用、禁用、已安装列表、审计日志查询、插件市场列表。
 */
@Service
public class PluginApplicationService {

    /** 当前平台版本（用于兼容性校验） */
    private static final String PLATFORM_VERSION = "1.0.0";

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final PluginPackageMapper pluginPackageMapper;
    private final PluginInstallationMapper pluginInstallationMapper;
    private final PluginAuditLogMapper pluginAuditLogMapper;

    public PluginApplicationService(PluginPackageMapper pluginPackageMapper,
                                    PluginInstallationMapper pluginInstallationMapper,
                                    PluginAuditLogMapper pluginAuditLogMapper) {
        this.pluginPackageMapper = pluginPackageMapper;
        this.pluginInstallationMapper = pluginInstallationMapper;
        this.pluginAuditLogMapper = pluginAuditLogMapper;
    }

    /**
     * 分页查询插件包，支持 keyword/status/riskLevel 过滤
     */
    public PageResult<PluginPackage> page(PluginPackagePageQuery query) {
        LambdaQueryWrapper<PluginPackage> wrapper = new LambdaQueryWrapper<PluginPackage>()
                .eq(PluginPackage::getTenantId, CurrentUserContext.getTenantId())
                .eq(query.getStatus() != null && !query.getStatus().isBlank(),
                        PluginPackage::getStatus, query.getStatus())
                .eq(query.getRiskLevel() != null && !query.getRiskLevel().isBlank(),
                        PluginPackage::getRiskLevel, query.getRiskLevel())
                .and(query.getKeyword() != null && !query.getKeyword().isBlank(),
                        w -> w.like(PluginPackage::getPluginCode, query.getKeyword())
                                .or().like(PluginPackage::getPluginName, query.getKeyword()))
                .orderByDesc(PluginPackage::getCreatedTime);
        Page<PluginPackage> page = pluginPackageMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPageNo(), query.getPageSize());
    }

    /**
     * 插件详情
     */
    public PluginPackage get(String id) {
        PluginPackage entity = pluginPackageMapper.selectById(id);
        if (entity == null) {
            throw new ResourceNotFoundException("插件不存在: " + id);
        }
        return entity;
    }

    /**
     * 新建插件包，默认状态 UPLOADED
     */
    @Transactional
    public PluginPackage create(SavePluginPackageRequest req) {
        checkCodeDuplicate(req.getPluginCode(), null);
        PluginPackage entity = new PluginPackage();
        BeanUtils.copyProperties(req, entity, "id", "version");
        entity.setId(IdGenerator.nextId());
        entity.setTenantId(CurrentUserContext.getTenantId());
        entity.setCreatedBy(CurrentUserContext.getUserId());
        // 默认值
        if (entity.getPluginVersion() == null) {
            entity.setPluginVersion("1.0.0");
        }
        if (entity.getSignatureStatus() == null) {
            entity.setSignatureStatus(PluginPackage.SIGNATURE_UNSIGNED);
        }
        if (entity.getRiskLevel() == null) {
            entity.setRiskLevel(PluginPackage.RISK_LOW);
        }
        if (entity.getStatus() == null) {
            entity.setStatus(PluginPackage.STATUS_UPLOADED);
        }
        if (entity.getInstallCount() == null) {
            entity.setInstallCount(0);
        }
        pluginPackageMapper.insert(entity);
        return entity;
    }

    /**
     * 更新插件包（乐观锁）
     */
    @Transactional
    public PluginPackage update(String id, SavePluginPackageRequest req) {
        PluginPackage existing = pluginPackageMapper.selectById(id);
        if (existing == null) {
            throw new ResourceNotFoundException("插件不存在: " + id);
        }
        checkVersion(req.getVersion(), existing.getVersion());
        checkCodeDuplicate(req.getPluginCode(), id);
        BeanUtils.copyProperties(req, existing, "id", "version", "installCount");
        existing.setUpdatedBy(CurrentUserContext.getUserId());
        int affected = pluginPackageMapper.updateById(existing);
        if (affected == 0) {
            throw new BusinessConflictException("数据已被他人修改，请刷新后重试");
        }
        return existing;
    }

    /**
     * 安装插件（含兼容性、依赖、版本冲突校验）
     */
    @Transactional
    public PluginInstallation install(String pluginId, InstallPluginRequest req) {
        PluginPackage plugin = pluginPackageMapper.selectById(pluginId);
        if (plugin == null) {
            throw new ResourceNotFoundException("插件不存在: " + pluginId);
        }
        // 校验插件状态必须为 VERIFIED 才能安装
        if (!PluginPackage.STATUS_VERIFIED.equals(plugin.getStatus())) {
            throw new BusinessConflictException("插件未通过验证，不可安装: " + plugin.getPluginCode());
        }
        // 平台版本兼容性校验
        PluginDependencyCheckResult depCheck = checkDependencies(plugin);
        if (!depCheck.isVersionCompatible()) {
            throw new BusinessConflictException(depCheck.getVersionConflictMessage());
        }
        if (!depCheck.isPassed()) {
            throw new BusinessConflictException("缺少依赖插件: " + String.join(", ", depCheck.getMissingDependencies()));
        }
        // 检查是否已安装（ACTIVE 或 DISABLED 均视为已安装）
        PluginInstallation existing = pluginInstallationMapper.selectOne(
                new LambdaQueryWrapper<PluginInstallation>()
                        .eq(PluginInstallation::getTenantId, CurrentUserContext.getTenantId())
                        .eq(PluginInstallation::getPluginId, pluginId)
                        .in(PluginInstallation::getStatus,
                                List.of(PluginInstallation.STATUS_ACTIVE, PluginInstallation.STATUS_DISABLED)));
        if (existing != null) {
            throw new BusinessConflictException("插件已安装: " + plugin.getPluginCode());
        }
        // 检查是否有其他版本已安装（版本冲突）
        PluginPackage otherVersion = pluginPackageMapper.selectOne(
                new LambdaQueryWrapper<PluginPackage>()
                        .eq(PluginPackage::getTenantId, CurrentUserContext.getTenantId())
                        .eq(PluginPackage::getPluginCode, plugin.getPluginCode())
                        .ne(PluginPackage::getId, pluginId)
                        .eq(PluginPackage::getDeleted, false));
        if (otherVersion != null) {
            PluginInstallation activeInst = pluginInstallationMapper.selectOne(
                    new LambdaQueryWrapper<PluginInstallation>()
                            .eq(PluginInstallation::getTenantId, CurrentUserContext.getTenantId())
                            .eq(PluginInstallation::getPluginId, otherVersion.getId())
                            .in(PluginInstallation::getStatus, List.of(PluginInstallation.STATUS_ACTIVE, PluginInstallation.STATUS_DISABLED)));
            if (activeInst != null) {
                throw new BusinessConflictException("该插件的其他版本已安装，请先卸载: " + plugin.getPluginCode());
            }
        }
        // 创建安装记录
        PluginInstallation installation = new PluginInstallation();
        installation.setId(IdGenerator.nextId());
        installation.setTenantId(CurrentUserContext.getTenantId());
        installation.setPluginId(pluginId);
        installation.setInstalledBy(CurrentUserContext.getUserId());
        installation.setInstalledTime(OffsetDateTime.now());
        installation.setStatus(PluginInstallation.STATUS_ACTIVE);
        installation.setConfigJson(req != null ? req.getConfigJson() : null);
        installation.setCreatedBy(CurrentUserContext.getUserId());
        pluginInstallationMapper.insert(installation);
        // 更新插件安装次数
        plugin.setInstallCount(plugin.getInstallCount() + 1);
        plugin.setUpdatedBy(CurrentUserContext.getUserId());
        pluginPackageMapper.updateById(plugin);
        // 记录审计日志
        recordAuditLog(plugin.getPluginCode(), plugin.getPluginVersion(),
                "INSTALL", installation.getId(), PluginAuditLog.RESULT_SUCCESS, null);
        return installation;
    }

    /**
     * 卸载插件
     */
    @Transactional
    public void uninstall(String pluginId) {
        PluginPackage plugin = pluginPackageMapper.selectById(pluginId);
        if (plugin == null) {
            throw new ResourceNotFoundException("插件不存在: " + pluginId);
        }
        PluginInstallation installation = pluginInstallationMapper.selectOne(
                new LambdaQueryWrapper<PluginInstallation>()
                        .eq(PluginInstallation::getTenantId, CurrentUserContext.getTenantId())
                        .eq(PluginInstallation::getPluginId, pluginId)
                        .in(PluginInstallation::getStatus,
                                List.of(PluginInstallation.STATUS_ACTIVE, PluginInstallation.STATUS_DISABLED)));
        if (installation == null) {
            throw new BusinessConflictException("插件未安装: " + plugin.getPluginCode());
        }
        // 检查是否有其他插件依赖于此插件
        List<String> dependents = findDependents(plugin.getPluginCode());
        if (!dependents.isEmpty()) {
            throw new BusinessConflictException("以下已安装插件依赖此插件，请先卸载: " + String.join(", ", dependents));
        }
        // 更新安装记录
        installation.setStatus(PluginInstallation.STATUS_INACTIVE);
        installation.setUninstalledTime(OffsetDateTime.now());
        installation.setUpdatedBy(CurrentUserContext.getUserId());
        pluginInstallationMapper.updateById(installation);
        // 记录审计日志
        recordAuditLog(plugin.getPluginCode(), plugin.getPluginVersion(),
                "UNINSTALL", installation.getId(), PluginAuditLog.RESULT_SUCCESS, null);
    }

    /**
     * 启用插件
     */
    @Transactional
    public void enable(String pluginId) {
        PluginPackage plugin = pluginPackageMapper.selectById(pluginId);
        if (plugin == null) {
            throw new ResourceNotFoundException("插件不存在: " + pluginId);
        }
        PluginInstallation installation = pluginInstallationMapper.selectOne(
                new LambdaQueryWrapper<PluginInstallation>()
                        .eq(PluginInstallation::getTenantId, CurrentUserContext.getTenantId())
                        .eq(PluginInstallation::getPluginId, pluginId)
                        .eq(PluginInstallation::getStatus, PluginInstallation.STATUS_DISABLED));
        if (installation == null) {
            throw new BusinessConflictException("插件未处于禁用状态: " + plugin.getPluginCode());
        }
        // 启用前重新校验依赖
        PluginDependencyCheckResult depCheck = checkDependencies(plugin);
        if (!depCheck.isPassed()) {
            throw new BusinessConflictException("依赖插件缺失，无法启用: " + String.join(", ", depCheck.getMissingDependencies()));
        }
        installation.setStatus(PluginInstallation.STATUS_ACTIVE);
        installation.setUpdatedBy(CurrentUserContext.getUserId());
        pluginInstallationMapper.updateById(installation);
        recordAuditLog(plugin.getPluginCode(), plugin.getPluginVersion(),
                "ENABLE", installation.getId(), PluginAuditLog.RESULT_SUCCESS, null);
    }

    /**
     * 禁用插件
     */
    @Transactional
    public void disable(String pluginId) {
        PluginPackage plugin = pluginPackageMapper.selectById(pluginId);
        if (plugin == null) {
            throw new ResourceNotFoundException("插件不存在: " + pluginId);
        }
        PluginInstallation installation = pluginInstallationMapper.selectOne(
                new LambdaQueryWrapper<PluginInstallation>()
                        .eq(PluginInstallation::getTenantId, CurrentUserContext.getTenantId())
                        .eq(PluginInstallation::getPluginId, pluginId)
                        .eq(PluginInstallation::getStatus, PluginInstallation.STATUS_ACTIVE));
        if (installation == null) {
            throw new BusinessConflictException("插件未处于启用状态: " + plugin.getPluginCode());
        }
        // 检查是否有其他插件依赖于此插件
        List<String> dependents = findDependents(plugin.getPluginCode());
        if (!dependents.isEmpty()) {
            throw new BusinessConflictException("以下已安装插件依赖此插件，无法禁用: " + String.join(", ", dependents));
        }
        installation.setStatus(PluginInstallation.STATUS_DISABLED);
        installation.setUpdatedBy(CurrentUserContext.getUserId());
        pluginInstallationMapper.updateById(installation);
        recordAuditLog(plugin.getPluginCode(), plugin.getPluginVersion(),
                "DISABLE", installation.getId(), PluginAuditLog.RESULT_SUCCESS, null);
    }

    /**
     * 查询插件市场列表（所有 VERIFIED 状态插件 + 当前租户安装状态标记）
     */
    public List<MarketPluginVO> listMarketPlugins() {
        String tenantId = CurrentUserContext.getTenantId();
        // 查询所有已验证的插件包
        List<PluginPackage> packages = pluginPackageMapper.selectList(
                new LambdaQueryWrapper<PluginPackage>()
                        .eq(PluginPackage::getTenantId, tenantId)
                        .eq(PluginPackage::getStatus, PluginPackage.STATUS_VERIFIED)
                        .eq(PluginPackage::getDeleted, false)
                        .orderByDesc(PluginPackage::getInstallCount));
        // 查询当前租户的所有安装记录
        List<PluginInstallation> installations = pluginInstallationMapper.selectList(
                new LambdaQueryWrapper<PluginInstallation>()
                        .eq(PluginInstallation::getTenantId, tenantId)
                        .in(PluginInstallation::getStatus,
                                List.of(PluginInstallation.STATUS_ACTIVE, PluginInstallation.STATUS_DISABLED)));
        // 构建 pluginId -> installationStatus 映射
        java.util.Map<String, String> installStatusMap = new java.util.HashMap<>();
        for (PluginInstallation inst : installations) {
            installStatusMap.put(inst.getPluginId(), inst.getStatus());
        }
        List<MarketPluginVO> result = new ArrayList<>();
        for (PluginPackage pkg : packages) {
            MarketPluginVO vo = new MarketPluginVO();
            BeanUtils.copyProperties(pkg, vo);
            String instStatus = installStatusMap.get(pkg.getId());
            vo.setInstalled(instStatus != null);
            vo.setInstallationStatus(instStatus);
            result.add(vo);
        }
        return result;
    }

    /**
     * 校验插件依赖与平台版本兼容性
     */
    public PluginDependencyCheckResult checkDependencies(PluginPackage plugin) {
        PluginDependencyCheckResult result = new PluginDependencyCheckResult();
        result.setVersionCompatible(true);
        result.setPassed(true);
        result.setMissingDependencies(new ArrayList<>());

        // 平台版本兼容性校验
        if (StringUtils.hasText(plugin.getMinPlatformVersion())) {
            if (compareVersion(PLATFORM_VERSION, plugin.getMinPlatformVersion()) < 0) {
                result.setVersionCompatible(false);
                result.setVersionConflictMessage(
                        "当前平台版本 " + PLATFORM_VERSION + " 低于最低要求 " + plugin.getMinPlatformVersion());
                result.setPassed(false);
                return result;
            }
        }
        if (StringUtils.hasText(plugin.getMaxPlatformVersion())) {
            if (compareVersion(PLATFORM_VERSION, plugin.getMaxPlatformVersion()) > 0) {
                result.setVersionCompatible(false);
                result.setVersionConflictMessage(
                        "当前平台版本 " + PLATFORM_VERSION + " 高于最高兼容 " + plugin.getMaxPlatformVersion());
                result.setPassed(false);
                return result;
            }
        }

        // 依赖缺失校验
        if (StringUtils.hasText(plugin.getDependenciesJson())) {
            List<String> dependencies;
            try {
                dependencies = OBJECT_MAPPER
                        .readValue(plugin.getDependenciesJson(),
                                new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            } catch (Exception e) {
                // 如果解析失败，视为无依赖
                dependencies = Collections.emptyList();
            }
            for (String depCode : dependencies) {
                // 查找依赖插件是否已安装
                PluginPackage depPkg = pluginPackageMapper.selectOne(
                        new LambdaQueryWrapper<PluginPackage>()
                                .eq(PluginPackage::getTenantId, CurrentUserContext.getTenantId())
                                .eq(PluginPackage::getPluginCode, depCode)
                                .eq(PluginPackage::getDeleted, false));
                if (depPkg == null) {
                    result.getMissingDependencies().add(depCode);
                    continue;
                }
                PluginInstallation depInst = pluginInstallationMapper.selectOne(
                        new LambdaQueryWrapper<PluginInstallation>()
                                .eq(PluginInstallation::getTenantId, CurrentUserContext.getTenantId())
                                .eq(PluginInstallation::getPluginId, depPkg.getId())
                                .eq(PluginInstallation::getStatus, PluginInstallation.STATUS_ACTIVE));
                if (depInst == null) {
                    result.getMissingDependencies().add(depCode);
                }
            }
            if (!result.getMissingDependencies().isEmpty()) {
                result.setPassed(false);
            }
        }
        return result;
    }

    /**
     * 查询已安装插件列表
     */
    public List<PluginInstallation> listInstalled() {
        return pluginInstallationMapper.selectList(
                new LambdaQueryWrapper<PluginInstallation>()
                        .eq(PluginInstallation::getTenantId, CurrentUserContext.getTenantId())
                        .eq(PluginInstallation::getStatus, PluginInstallation.STATUS_ACTIVE)
                        .orderByDesc(PluginInstallation::getInstalledTime));
    }

    /**
     * 分页查询审计日志
     */
    public PageResult<PluginAuditLog> pageAuditLogs(int pageNo, int pageSize, String pluginCode) {
        LambdaQueryWrapper<PluginAuditLog> wrapper = new LambdaQueryWrapper<PluginAuditLog>()
                .eq(PluginAuditLog::getTenantId, CurrentUserContext.getTenantId())
                .eq(pluginCode != null && !pluginCode.isBlank(),
                        PluginAuditLog::getPluginCode, pluginCode)
                .orderByDesc(PluginAuditLog::getCreatedTime);
        Page<PluginAuditLog> page = pluginAuditLogMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    /**
     * 查找依赖指定插件编码的已安装插件列表
     */
    private List<String> findDependents(String pluginCode) {
        String tenantId = CurrentUserContext.getTenantId();
        List<PluginPackage> allPackages = pluginPackageMapper.selectList(
                new LambdaQueryWrapper<PluginPackage>()
                        .eq(PluginPackage::getTenantId, tenantId)
                        .eq(PluginPackage::getDeleted, false));
        List<PluginInstallation> activeInstalls = pluginInstallationMapper.selectList(
                new LambdaQueryWrapper<PluginInstallation>()
                        .eq(PluginInstallation::getTenantId, tenantId)
                        .eq(PluginInstallation::getStatus, PluginInstallation.STATUS_ACTIVE));
        java.util.Set<String> activePluginIds = activeInstalls.stream()
                .map(PluginInstallation::getPluginId).collect(java.util.stream.Collectors.toSet());

        List<String> dependents = new ArrayList<>();
        for (PluginPackage pkg : allPackages) {
            if (!activePluginIds.contains(pkg.getId())) {
                continue;
            }
            if (StringUtils.hasText(pkg.getDependenciesJson())) {
                try {
                    List<String> deps = OBJECT_MAPPER
                            .readValue(pkg.getDependenciesJson(),
                                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
                    if (deps.contains(pluginCode)) {
                        dependents.add(pkg.getPluginName() + "(" + pkg.getPluginCode() + ")");
                    }
                } catch (Exception ignored) {
                }
            }
        }
        return dependents;
    }

    /**
     * 记录审计日志
     */
    private void recordAuditLog(String pluginCode, String pluginVersion,
                                String bizType, String bizId, String result, String errorCode) {
        PluginAuditLog log = new PluginAuditLog();
        log.setId(IdGenerator.nextId());
        log.setTenantId(CurrentUserContext.getTenantId());
        log.setPluginCode(pluginCode);
        log.setPluginVersion(pluginVersion);
        log.setOperatorId(CurrentUserContext.getUserId());
        log.setBizType(bizType);
        log.setBizId(bizId);
        log.setResult(result);
        log.setErrorCode(errorCode);
        log.setCreatedBy(CurrentUserContext.getUserId());
        pluginAuditLogMapper.insert(log);
    }

    /**
     * 编码重复校验
     */
    private void checkCodeDuplicate(String pluginCode, String excludeId) {
        LambdaQueryWrapper<PluginPackage> wrapper = new LambdaQueryWrapper<PluginPackage>()
                .eq(PluginPackage::getTenantId, CurrentUserContext.getTenantId())
                .eq(PluginPackage::getPluginCode, pluginCode);
        if (excludeId != null && !excludeId.isBlank()) {
            wrapper.ne(PluginPackage::getId, excludeId);
        }
        Long count = pluginPackageMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ErrorCode.PLG_CODE_DUPLICATE);
        }
    }

    private void checkVersion(Integer requestVersion, Integer currentVersion) {
        if (requestVersion == null || !requestVersion.equals(currentVersion)) {
            throw new BusinessConflictException(
                    "版本号不匹配，请求版本=" + requestVersion + ", 当前版本=" + currentVersion);
        }
    }

    /**
     * 简单的语义化版本比较（支持 x.y.z 格式）
     * @return 负数: v1 < v2, 0: v1 == v2, 正数: v1 > v2
     */
    private int compareVersion(String v1, String v2) {
        if (v1 == null) return v2 == null ? 0 : -1;
        if (v2 == null) return 1;
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int n1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int n2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (n1 != n2) {
                return Integer.compare(n1, n2);
            }
        }
        return 0;
    }
}
