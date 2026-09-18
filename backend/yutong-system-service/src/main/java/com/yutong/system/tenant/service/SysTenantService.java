package com.yutong.system.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.system.tenant.domain.SysTenant;
import com.yutong.system.tenant.domain.SysTenantPackage;
import com.yutong.system.tenant.dto.SaveTenantRequest;
import com.yutong.system.tenant.mapper.SysTenantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 租户注册表服务 — 保存/启停/分配套餐/查询/删除。
 * 落点: 业界同类实现 SysTenantService + ADR 0004 P2-F。
 *
 * <p>生产语义 (失败关闭):
 * <ul>
 *   <li>tenantCode 创建后不可改 (130+ 表用它做行级隔离键);</li>
 *   <li>default 内置租户禁止停用/删除;</li>
 *   <li>删除仅允许 DISABLED (先停用后删除, 防误操作);</li>
 *   <li>分配套餐时校验套餐存在且非 ARCHIVED;</li>
 *   <li>accountCount 非法 (&lt; -1) 拒绝。</li>
 * </ul>
 */
@Service
public class SysTenantService {

    private final SysTenantMapper mapper;
    private final TenantPackageService packageService;

    public SysTenantService(SysTenantMapper mapper, TenantPackageService packageService) {
        this.mapper = mapper;
        this.packageService = packageService;
    }

    /**
     * 分页查询 (平台管理员视角, 全局)。
     */
    public Page<SysTenant> page(int pageNo, int pageSize, String status, String keyword) {
        QueryWrapper<SysTenant> w = new QueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq("status", status);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("tenant_code", keyword)
                    .or().like("company_name", keyword)
                    .or().like("contact_user_name", keyword));
        }
        w.orderByDesc("updated_time");
        return mapper.selectPage(Page.of(pageNo, pageSize), w);
    }

    /**
     * 按 ID 查询。
     */
    public SysTenant getById(String id) {
        SysTenant t = mapper.selectById(id);
        if (t == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "租户不存在: " + id);
        }
        return t;
    }

    /**
     * 按编码查询。
     */
    public SysTenant getByCode(String code) {
        SysTenant t = mapper.selectOne(
                new QueryWrapper<SysTenant>()
                        .eq("tenant_code", code)
                        .last("LIMIT 1")
        );
        if (t == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "租户不存在: " + code);
        }
        return t;
    }

    /**
     * 保存 (创建/更新)。更新时 tenantCode 忽略 (不可改)。
     */
    @Transactional
    public String save(SaveTenantRequest req) {
        if (req.getAccountCount() != null && req.getAccountCount() < -1) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "账号上限非法 (最小 -1 不限制)");
        }
        if (req.getPackageId() != null && !req.getPackageId().isBlank()) {
            assertPackageUsable(req.getPackageId());
        }
        SysTenant t;
        boolean isCreate = (req.getId() == null || req.getId().isBlank());
        if (isCreate) {
            if (isCodeTaken(req.getTenantCode(), null)) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "租户编码已被占用: " + req.getTenantCode());
            }
            t = new SysTenant();
            // BaseEntity.id 无 fill 注解, 必须显式生成 ULID
            t.setId(IdGenerator.nextId());
            t.setTenantCode(req.getTenantCode());
            // 注册表自身豁免行级过滤, 行 tenant_id 自描述为自身编码
            t.setTenantId(req.getTenantCode());
            t.setStatus(SysTenant.STATUS_NORMAL);
        } else {
            t = getById(req.getId());
            // 编码不可改: 显式拒绝 (而非静默忽略, 让调用方立刻发现误用)
            if (req.getTenantCode() != null && !req.getTenantCode().isBlank()
                    && !req.getTenantCode().equals(t.getTenantCode())) {
                throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                        "租户编码创建后不可修改: " + t.getTenantCode());
            }
            if (req.getStatus() != null && !req.getStatus().isBlank()) {
                assertStatusValue(req.getStatus());
                t.setStatus(req.getStatus());
            }
        }
        t.setCompanyName(req.getCompanyName());
        t.setContactUserName(req.getContactUserName());
        t.setContactPhone(req.getContactPhone());
        t.setLicenseNumber(req.getLicenseNumber());
        t.setAddress(req.getAddress());
        t.setDomain(req.getDomain());
        t.setIntro(req.getIntro());
        t.setPackageId(blankToNull(req.getPackageId()));
        t.setExpireTime(req.getExpireTime());
        t.setAccountCount(req.getAccountCount());
        // 创建走 insert, 更新走 updateById (id 显式生成后 getId() 永非空,
        // 不可用 null 判断分支, 否则创建会静默 update 0 行)
        if (isCreate) mapper.insert(t); else mapper.updateById(t);
        return t.getId();
    }

    /**
     * 停用 (NORMAL → DISABLED)。default 禁止停用。
     */
    @Transactional
    public void disable(String id) {
        SysTenant t = getById(id);
        assertNotBuiltin(t, "停用");
        t.setStatus(SysTenant.STATUS_DISABLED);
        mapper.updateById(t);
    }

    /**
     * 启用 (DISABLED → NORMAL)。
     */
    @Transactional
    public void enable(String id) {
        SysTenant t = getById(id);
        t.setStatus(SysTenant.STATUS_NORMAL);
        mapper.updateById(t);
    }

    /**
     * 分配套餐 (可同时续期)。套餐必须存在且非 ARCHIVED。
     */
    @Transactional
    public void assignPackage(String id, String packageId, LocalDateTime expireTime) {
        SysTenant t = getById(id);
        if (packageId == null || packageId.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "套餐 ID 不能为空");
        }
        assertPackageUsable(packageId);
        t.setPackageId(packageId);
        t.setExpireTime(expireTime);
        mapper.updateById(t);
    }

    /**
     * 删除。仅允许 DISABLED (先停用后删除); default 禁止删除。
     */
    @Transactional
    public void delete(String id) {
        SysTenant t = getById(id);
        assertNotBuiltin(t, "删除");
        if (!SysTenant.STATUS_DISABLED.equals(t.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "仅允许删除已停用租户, 请先停用: " + t.getTenantCode());
        }
        mapper.deleteById(id);
    }

    // ========== 私有 ==========

    private void assertPackageUsable(String packageId) {
        SysTenantPackage p = packageService.getById(packageId);
        if (SysTenantPackage.STATUS_ARCHIVED.equals(p.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "套餐已归档, 不可分配: " + p.getPackageCode());
        }
    }

    private void assertNotBuiltin(SysTenant t, String op) {
        if (SysTenant.BUILTIN_DEFAULT.equals(t.getTenantCode())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "平台内置租户禁止" + op + ": default");
        }
    }

    private void assertStatusValue(String status) {
        if (!SysTenant.STATUS_NORMAL.equals(status) && !SysTenant.STATUS_DISABLED.equals(status)) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "租户状态非法 (NORMAL/DISABLED): " + status);
        }
    }

    private boolean isCodeTaken(String code, String excludeId) {
        if (code == null || code.isBlank()) return false;
        QueryWrapper<SysTenant> w = new QueryWrapper<SysTenant>().eq("tenant_code", code);
        if (excludeId != null && !excludeId.isBlank()) w.ne("id", excludeId);
        return mapper.selectCount(w) > 0;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
