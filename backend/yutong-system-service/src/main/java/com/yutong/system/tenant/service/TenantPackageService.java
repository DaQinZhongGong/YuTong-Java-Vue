package com.yutong.system.tenant.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.system.tenant.domain.SysTenantPackage;
import com.yutong.system.tenant.dto.SaveTenantPackageRequest;
import com.yutong.system.tenant.mapper.SysTenantPackageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 租户套餐服务 — 保存/发布/查询/归档
 * 落点: 70-商业授权与版本能力裁剪 + ADR 0004 P2-F 批次 6-B
 *
 * 工作流:
 *   1. save: 校验编码唯一, 保存 (DRAFT/ACTIVE/ARCHIVED 任意状态都允许)
 *   2. publish: 状态 DRAFT → ACTIVE, 记录 publishedAt
 *   3. archive: 状态 → ARCHIVED
 *   4. 公开查询 listActive: 仅返回 ACTIVE (供租户订阅时选择)
 *
 * 安全: packageCode 全局唯一 (跨租户)
 */
@Service
public class TenantPackageService {

    private final SysTenantPackageMapper mapper;

    public TenantPackageService(SysTenantPackageMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 分页查询
     */
    public Page<SysTenantPackage> page(int pageNo, int pageSize, String status) {
        QueryWrapper<SysTenantPackage> w = new QueryWrapper<>();
        if (status != null && !status.isBlank()) w.eq("status", status);
        w.orderByAsc("sort_no").orderByDesc("created_time");
        return mapper.selectPage(Page.of(pageNo, pageSize), w);
    }

    /**
     * 列出已发布套餐 (供前端租户订阅选择)
     */
    public List<SysTenantPackage> listActive() {
        return mapper.selectList(
                new QueryWrapper<SysTenantPackage>()
                        .eq("status", SysTenantPackage.STATUS_ACTIVE)
                        .orderByAsc("sort_no")
        );
    }

    /**
     * 按 ID 查询
     */
    public SysTenantPackage getById(String id) {
        SysTenantPackage p = mapper.selectById(id);
        if (p == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "套餐不存在: " + id);
        }
        return p;
    }

    /**
     * 按编码查询
     */
    public SysTenantPackage getByCode(String code) {
        SysTenantPackage p = mapper.selectOne(
                new QueryWrapper<SysTenantPackage>()
                        .eq("package_code", code)
                        .last("LIMIT 1")
        );
        if (p == null) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "套餐不存在: " + code);
        }
        return p;
    }

    /**
     * 保存 (创建/更新)
     */
    @Transactional
    public String save(SaveTenantPackageRequest req) {
        // 编码格式校验 (大写字母开头, 2-50 字符, 字母数字下划线)
        if (req.getPackageCode() == null || !req.getPackageCode().matches("^[A-Z][A-Z0-9_]{1,49}$")) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "套餐编码必须以大写字母开头, 只能含大写字母/数字/下划线, 2-50 字符");
        }
        // 编码唯一性 (排除自己)
        if (isCodeTaken(req.getPackageCode(), req.getId())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "套餐编码已被占用: " + req.getPackageCode());
        }
        SysTenantPackage p;
        // BUGFIX 2026-09-07: 创建走 insert, 更新走 updateById。
        // id 显式生成后 getId() 永非空, 原 `getId() == null` 判断使 insert 成死代码,
        // 创建会静默 update 0 行 (数据丢失)。以 isCreate 显式分支。
        boolean isCreate = (req.getId() == null || req.getId().isBlank());
        if (isCreate) {
            p = new SysTenantPackage();
            // BaseEntity.id 无 fill 注解, MetaObjectHandler 不自动填充, 必须显式生成 ULID
            p.setId(IdGenerator.nextId());
            p.setStatus(SysTenantPackage.STATUS_DRAFT);
        } else {
            p = getById(req.getId());
        }
        p.setPackageCode(req.getPackageCode());
        p.setPackageName(req.getPackageName());
        p.setDescription(req.getDescription());
        p.setPriceCnyPerPeriod(req.getPriceCnyPerPeriod());
        p.setPeriodMonths(req.getPeriodMonths());
        if (req.getStatus() != null && !req.getStatus().isBlank()) p.setStatus(req.getStatus());
        p.setMenuIdsJson(req.getMenuIdsJson());
        p.setQuotaJson(req.getQuotaJson());
        p.setSortNo(req.getSortNo());
        if (isCreate) mapper.insert(p); else mapper.updateById(p);
        return p.getId();
    }

    /**
     * 发布 (DRAFT → ACTIVE)
     */
    @Transactional
    public void publish(String id) {
        SysTenantPackage p = getById(id);
        if (SysTenantPackage.STATUS_ARCHIVED.equals(p.getStatus())) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "已归档套餐不能发布");
        }
        p.setStatus(SysTenantPackage.STATUS_ACTIVE);
        p.setPublishedAt(LocalDateTime.now());
        mapper.updateById(p);
    }

    /**
     * 归档
     */
    @Transactional
    public void archive(String id) {
        SysTenantPackage p = getById(id);
        p.setStatus(SysTenantPackage.STATUS_ARCHIVED);
        mapper.updateById(p);
    }

    private boolean isCodeTaken(String code, String excludeId) {
        if (code == null || code.isBlank()) return false;
        QueryWrapper<SysTenantPackage> w = new QueryWrapper<SysTenantPackage>().eq("package_code", code);
        if (excludeId != null && !excludeId.isBlank()) w.ne("id", excludeId);
        return mapper.selectOne(w) != null;
    }
}
