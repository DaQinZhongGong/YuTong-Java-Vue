package com.yutong.system.license.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.api.dto.LicenseInfo;
import com.yutong.api.facade.LicenseService;
import com.yutong.auth.RequiresPermission;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.license.config.LicenseProperties;
import com.yutong.system.license.domain.SysLicenseUsage;
import com.yutong.system.license.dto.LicenseAuditLogVo;
import com.yutong.system.license.dto.LicenseUploadResult;
import com.yutong.system.license.service.LocalLicenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商业授权管理端接口。设计来源: 70-商业授权与版本能力裁剪详设「授权校验流程」。
 *
 * <p>提供管理端/运维观察当前授权状态、模块开关、额度使用、审计日志的接口，
 * 以及管理员上传/刷新 License 的管理接口。
 *
 * <p>GA2-L170 落地: 关闭 DEV-L170-003 偏差（LicenseService 接口未实现）的可观察部分。
 * GA2-L174 落地: 70 号文档「授权校验流程」4 时机之「手动刷新」补齐——
 * 新增 POST /upload（上传 License）+ POST /refresh（手动刷新）+ GET /audit-logs（审计日志查询）。
 *
 * <p>路由对齐 contracts/registries/routes.yaml 规范: /api/v1/license/*。
 */
@Tag(name = "商业授权管理")
@RestController
@RequestMapping("/api/v1/license")
public class LicenseController {

    /** 平台支持的模块编码全集（70 号文档模块授权行为矩阵） */
    private static final String[] ALL_MODULE_CODES = {
            "system", "sample", "lowcode", "ai", "report", "workflow", "datasource", "plugin"
    };

    private final LicenseService licenseService;
    private final LicenseProperties licenseProperties;
    private final LocalLicenseService localLicenseService;

    public LicenseController(LicenseService licenseService, LicenseProperties licenseProperties,
                             LocalLicenseService localLicenseService) {
        this.licenseService = licenseService;
        this.licenseProperties = licenseProperties;
        this.localLicenseService = localLicenseService;
    }

    /**
     * 查询当前授权信息。
     * 返回 sys_license 表当前 ACTIVE License 记录，无记录时返回降级 Community 实例。
     */
    @Operation(summary = "查询当前授权信息", operationId = "getCurrentLicense")
    @RequiresPermission("system:license:list")
    @GetMapping("/current")
    public Result<LicenseInfo> current() {
        return Result.ok(licenseService.current(), TraceContext.getTraceId());
    }

    /**
     * 查询指定模块的授权与开关状态。
     *
     * @param moduleCode 模块编码（lowcode/ai/report/workflow/datasource/plugin）
     */
    @Operation(summary = "查询模块授权状态", operationId = "getModuleStatus")
    @RequiresPermission("system:license:list")
    @GetMapping("/modules/{moduleCode}")
    public Result<ModuleStatus> getModuleStatus(@PathVariable String moduleCode) {
        LicenseInfo info = licenseService.current();
        boolean licensed = info.modules() != null && info.modules().contains(moduleCode);
        boolean switchOn = licenseProperties.isModuleEnabled(moduleCode);
        boolean active = licensed && switchOn;
        return Result.ok(new ModuleStatus(moduleCode, licensed, switchOn, active), TraceContext.getTraceId());
    }

    /**
     * 查询所有模块的授权与开关状态列表。
     * 便于管理端一次性渲染模块开关矩阵。
     */
    @Operation(summary = "查询所有模块授权状态", operationId = "listModuleStatuses")
    @RequiresPermission("system:license:list")
    @GetMapping("/modules")
    public Result<List<ModuleStatus>> listModuleStatuses() {
        LicenseInfo info = licenseService.current();
        List<String> licensedModules = info.modules() != null ? info.modules() : List.of();
        Map<String, Boolean> switches = licenseProperties.getModules();
        List<ModuleStatus> statuses = new ArrayList<>(ALL_MODULE_CODES.length);
        for (String code : ALL_MODULE_CODES) {
            boolean licensed = licensedModules.contains(code);
            boolean switchOn = licenseProperties.isModuleEnabled(code);
            boolean active = licensed && switchOn;
            statuses.add(new ModuleStatus(code, licensed, switchOn, active));
        }
        return Result.ok(statuses, TraceContext.getTraceId());
    }

    /**
     * 查询当前租户当前周期的额度使用情况。
     * 返回 sys_license_usage 表中当前租户 + 当前周期（Asia/Shanghai yyyy-MM）的所有额度记录。
     *
     * <p>GA2-L172 落地: 70 号文档「额度扣减规则」管理端观察接口。
     */
    @Operation(summary = "查询额度使用情况", operationId = "getLicenseUsage")
    @RequiresPermission("system:license:list")
    @GetMapping("/usage")
    public Result<List<QuotaUsage>> getUsage() {
        List<SysLicenseUsage> usages = localLicenseService.listCurrentUsage();
        List<QuotaUsage> result = new ArrayList<>(usages.size());
        for (SysLicenseUsage u : usages) {
            result.add(new QuotaUsage(
                    u.getQuotaCode(),
                    u.getUsedAmount() != null ? u.getUsedAmount().longValue() : 0L,
                    u.getLimitAmount() != null ? u.getLimitAmount().longValue() : null,
                    u.getUsagePeriod(),
                    u.getLastUsedTime() != null ? u.getLastUsedTime().toString() : null
            ));
        }
        return Result.ok(result, TraceContext.getTraceId());
    }

    /**
     * 上传新 License 文件（70 号文档「授权校验流程 - 手动刷新」）。
     *
     * <p>接收 License JSON 字符串作为请求体，校验后写入 sys_license 表。
     * 现有 ACTIVE License 状态改为 REVOKED（保留历史），新 License 插入为 ACTIVE。
     * 写审计日志 action=REFRESH。
     *
     * <p>GA2-L174 落地: 70 号文档「授权校验流程」4 时机之「手动刷新」补齐。
     *
     * @param licenseJson License 文件 JSON 字符串（70 号文档 License 文件结构）
     * @return 上传结果摘要
     */
    @Operation(summary = "上传新 License 文件", operationId = "uploadLicense")
    @RequiresPermission("system:license:activate")
    @PostMapping("/upload")
    public Result<LicenseUploadResult> upload(@RequestBody String licenseJson) {
        LicenseUploadResult result = localLicenseService.uploadLicense(licenseJson);
        return Result.ok(result, TraceContext.getTraceId());
    }

    /**
     * 手动刷新 License（70 号文档「授权校验流程 - 手动刷新」）。
     *
     * <p>重新从 sys_license 表读取最新 ACTIVE License，写审计日志 action=REFRESH。
     * 第一版无内存缓存，refresh 主要用于审计记录管理员手动触发刷新的动作。
     *
     * <p>GA2-L174 落地: 70 号文档「授权校验流程」4 时机之「手动刷新」补齐。
     *
     * @return 操作结果（无返回数据，仅表示刷新已执行）
     */
    @Operation(summary = "手动刷新 License", operationId = "refreshLicense")
    @RequiresPermission("system:license:activate")
    @PostMapping("/refresh")
    public Result<Void> refresh() {
        localLicenseService.refreshLicense();
        return Result.ok(null, TraceContext.getTraceId());
    }

    /**
     * 手动触发 License 定时校验（70 号文档「授权校验流程 - 定时校验」）。
     *
     * <p>管理员可手动触发定时校验逻辑（无需等待 cron 02:00 执行），
     * 与 {@link com.yutong.system.license.scheduler.LicenseVerifyScheduler} 每天 02:00 自动执行的逻辑完全一致。
     *
     * <p>校验结果:
     * <ul>
     *   <li>OK - 校验通过</li>
     *   <li>NEAR_EXPIRY - 即将到期（剩余 &lt; 30 天），WARN</li>
     *   <li>EXPIRED - 已过期，状态已改为 EXPIRED，商业模块将被拦截</li>
     *   <li>NO_LICENSE - 无 ACTIVE License，降级 Community 模式</li>
     *   <li>ERROR - 校验异常</li>
     * </ul>
     *
     * <p>GA2-L175 落地: 70 号文档「授权校验流程」4 时机之「定时校验」补齐。
     *
     * @return 校验结果（status / licenseId / edition / daysLeft / detail）
     */
    @Operation(summary = "手动触发 License 定时校验", operationId = "verifyLicense")
    @RequiresPermission("system:license:verify")
    @PostMapping("/verify")
    public Result<LocalLicenseService.LicenseVerifyResult> verify() {
        LocalLicenseService.LicenseVerifyResult result = localLicenseService.verifyLicense();
        return Result.ok(result, TraceContext.getTraceId());
    }

    /**
     * 分页查询授权审计日志（70 号文档「授权运行数据模型 - sys_license_audit_log」管理端观察接口）。
     *
     * <p>支持按 action / licenseId / moduleCode 过滤，按操作时间倒序排列。
     *
     * <p>GA2-L174 落地: 70 号文档「授权校验流程」管理端观察接口补齐——
     * 将 sys_license_audit_log 表数据暴露给管理员查询。
     *
     * @param page       页码（1-based，默认 1）
     * @param size       每页大小（默认 20，最大 100）
     * @param action     动作过滤（LOAD/VERIFY/REFRESH/DENY/EXPIRE/QUOTA_EXCEEDED，可空）
     * @param licenseId  授权 ID 过滤（可空）
     * @param moduleCode 模块编码过滤（可空）
     * @return 分页审计日志列表
     */
    @Operation(summary = "查询授权审计日志", operationId = "listLicenseAuditLogs")
    @RequiresPermission("system:license:list")
    @GetMapping("/audit-logs")
    public Result<Page<LicenseAuditLogVo>> listAuditLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String licenseId,
            @RequestParam(required = false) String moduleCode) {
        if (page < 1) page = 1;
        if (size < 1 || size > 100) size = 20;
        Page<LicenseAuditLogVo> result = localLicenseService.listAuditLogs(page, size, action, licenseId, moduleCode);
        return Result.ok(result, TraceContext.getTraceId());
    }

    /**
     * 模块授权状态 VO。
     *
     * @param moduleCode 模块编码
     * @param licensed   License 是否授权该模块（来自 sys_license.modules_json）
     * @param switchOn   application.yml yutong.modules.{code} 开关是否开启（默认 true）
     * @param active     最终是否生效（licensed && switchOn）
     */
    public record ModuleStatus(
            String moduleCode,
            boolean licensed,
            boolean switchOn,
            boolean active
    ) {
    }

    /**
     * 额度使用情况 VO。
     *
     * @param quotaCode   额度编码（如 ai.monthly.tokens）
     * @param used        已使用量
     * @param limit       上限（null 表示无限制，Community 降级模式）
     * @param period      统计周期（如 2026-07）
     * @param lastUsedTime 最近使用时间
     */
    public record QuotaUsage(
            String quotaCode,
            long used,
            Long limit,
            String period,
            String lastUsedTime
    ) {
    }
}
