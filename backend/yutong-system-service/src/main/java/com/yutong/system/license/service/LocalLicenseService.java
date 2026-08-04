package com.yutong.system.license.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.api.dto.LicenseInfo;
import com.yutong.api.facade.LicenseService;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.license.config.LicenseProperties;
import com.yutong.system.license.domain.SysLicense;
import com.yutong.system.license.domain.SysLicenseAuditLog;
import com.yutong.system.license.domain.SysLicenseUsage;
import com.yutong.system.license.dto.LicenseAuditLogVo;
import com.yutong.system.license.dto.LicenseUploadResult;
import com.yutong.system.license.mapper.SysLicenseAuditLogMapper;
import com.yutong.system.license.mapper.SysLicenseMapper;
import com.yutong.system.license.mapper.SysLicenseUsageMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link LicenseService} 的 boot 模式本地实现。
 *
 * <p>设计来源: 70-商业授权与版本能力裁剪详设「授权校验接口」「授权运行数据模型」「额度扣减规则」、
 * 98-后端实现蓝图与代码骨架详设（boot/cloud Facade 装配规范）。
 *
 * <p>装配规则:
 * <ul>
 *   <li>{@code @Profile("!cloud")} — 所有非 cloud profile (local/test/prod 单体) 均启用</li>
 *   <li>{@code @Service} — Spring 自动扫描注册为 Bean</li>
 *   <li>cloud 模式由 {@code FeignLicenseService} 接管（v1.1+）</li>
 * </ul>
 *
 * <p>降级策略（70 号文档「失败与降级策略」）:
 * 当 sys_license 表无 ACTIVE 记录时（第一版未实现 License Server），
 * {@link #current()} 返回 {@code fallbackCommunity=true} 的默认 Community 实例，
 * modules 为基础模块集（system/sample/lowcode），limits 为空（无额度限制），
 * 保证基础能力可用，仅商业模块（ai/report/workflow/datasource/plugin）被拦截。
 *
 * <p>审计写入（70 号文档「模块授权行为矩阵」）:
 * 所有直连 API 拒绝、额度超限等事件通过 {@link #writeAudit} 写入 sys_license_audit_log，
 * 包含 moduleCode/quotaCode/licenseId/userIdHash/tenantIdHash/traceId 等字段。
 * 审计写入失败不阻断业务流程（与 LocalAuditFacade 容错策略一致）。
 *
 * <p>GA2-L170 落地: 关闭 DEV-L170-003 偏差（LicenseService 接口未实现）。
 */
@Service
@Profile("!cloud")
public class LocalLicenseService implements LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LocalLicenseService.class);

    /** 降级 Community 模块集（70 号文档版本能力矩阵：Community 包含基础工程/样例业务/低代码基础） */
    private static final List<String> FALLBACK_COMMUNITY_MODULES = List.of("system", "sample", "lowcode");

    private static final String FALLBACK_COMMUNITY_LIMITS_JSON = "{}";
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String ACTIVE_STATUS = "ACTIVE";
    /** GA2-L174: 上传新 License 时，旧 ACTIVE License 状态改为 REVOKED（保留历史） */
    private static final String REVOKED_STATUS = "REVOKED";
    private static final String DEFAULT_SCHEMA_VERSION = "1.0.0";
    private static final ZoneId TENANT_TIMEZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    /** GA2-L174: 支持的版本枚举（70 号文档版本能力矩阵） */
    private static final Set<String> SUPPORTED_EDITIONS = Set.of("Community", "Professional", "Enterprise", "Industry");
    /** GA2-L175: License 状态 - 已过期 */
    private static final String EXPIRED_STATUS = "EXPIRED";
    /** GA2-L175: 到期预警阈值（剩余天数 < 30 天时 WARN） */
    private static final long EXPIRY_WARNING_DAYS = 30L;

    private final SysLicenseMapper licenseMapper;
    private final SysLicenseUsageMapper usageMapper;
    private final SysLicenseAuditLogMapper auditLogMapper;
    private final LicenseProperties licenseProperties;
    private final ObjectMapper objectMapper;

    public LocalLicenseService(SysLicenseMapper licenseMapper,
                               SysLicenseUsageMapper usageMapper,
                               SysLicenseAuditLogMapper auditLogMapper,
                               LicenseProperties licenseProperties,
                               ObjectMapper objectMapper) {
        this.licenseMapper = licenseMapper;
        this.usageMapper = usageMapper;
        this.auditLogMapper = auditLogMapper;
        this.licenseProperties = licenseProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public LicenseInfo current() {
        String tenantId = resolveTenantId();
        LambdaQueryWrapper<SysLicense> wrapper = new LambdaQueryWrapper<SysLicense>()
                .eq(SysLicense::getTenantId, tenantId)
                .eq(SysLicense::getStatus, ACTIVE_STATUS)
                .orderByDesc(SysLicense::getCreatedTime)
                .last("LIMIT 1");
        SysLicense entity;
        try {
            entity = licenseMapper.selectOne(wrapper);
        } catch (Exception e) {
            // 表不存在或查询异常时降级到 Community（保证系统可用性）
            log.warn("License 查询失败 tenantId={}, 降级到 Community 模式 - {}", tenantId, e.getMessage());
            return fallbackCommunity();
        }
        if (entity == null) {
            log.debug("无 ACTIVE License 记录 tenantId={}, 降级到 Community 模式", tenantId);
            return fallbackCommunity();
        }
        return toLicenseInfo(entity);
    }

    @Override
    public boolean hasModule(String moduleCode) {
        if (moduleCode == null || moduleCode.isBlank()) {
            return false;
        }
        LicenseInfo info = current();
        boolean licensed = info.modules() != null && info.modules().contains(moduleCode);
        boolean switchOn = licenseProperties.isModuleEnabled(moduleCode);
        return licensed && switchOn;
    }

    @Override
    @Transactional
    public void checkQuota(String quotaCode, long amount) {
        if (quotaCode == null || quotaCode.isBlank() || amount <= 0) {
            return;
        }
        LicenseInfo info = current();
        Long limit = extractLimit(info.limitsJson(), quotaCode);
        if (limit == null || limit <= 0) {
            // 无额度限制（Community 降级模式或 License 未配置该额度），直接放行
            return;
        }
        String tenantId = resolveTenantId();
        String usagePeriod = currentPeriod();
        LambdaQueryWrapper<SysLicenseUsage> wrapper = new LambdaQueryWrapper<SysLicenseUsage>()
                .eq(SysLicenseUsage::getTenantId, tenantId)
                .eq(SysLicenseUsage::getQuotaCode, quotaCode)
                .eq(SysLicenseUsage::getUsagePeriod, usagePeriod);
        SysLicenseUsage usage;
        try {
            usage = usageMapper.selectOne(wrapper);
        } catch (Exception e) {
            log.warn("额度使用量查询失败 tenantId={} quotaCode={}, 跳过额度校验 - {}",
                    tenantId, quotaCode, e.getMessage());
            return;
        }
        long used = usage != null && usage.getUsedAmount() != null ? usage.getUsedAmount().longValue() : 0L;
        if (used + amount > limit) {
            String detail = String.format(
                    "{\"quotaCode\":\"%s\",\"used\":%d,\"amount\":%d,\"limit\":%d,\"period\":\"%s\"}",
                    quotaCode, used, amount, limit, usagePeriod);
            writeAudit("QUOTA_EXCEEDED", null, quotaCode, "DENIED",
                    ErrorCode.LIC_QUOTA_EXCEEDED.code(), detail);
            log.warn("额度超限 tenantId={} quotaCode={} used={} amount={} limit={}",
                    tenantId, quotaCode, used, amount, limit);
            throw new BusinessException(ErrorCode.LIC_QUOTA_EXCEEDED,
                    "额度不足: quotaCode=" + quotaCode + " used=" + used + " amount=" + amount + " limit=" + limit);
        }
    }

    /**
     * 写入授权审计日志。所有直连 API 拒绝、额度超限等事件调用本方法。
     * 容错: 审计写入失败不阻断业务流程，仅记录 WARN 日志。
     *
     * @param action     动作 LOAD/VERIFY/REFRESH/DENY/EXPIRE/QUOTA_EXCEEDED
     * @param moduleCode 模块编码（可空）
     * @param quotaCode  额度编码（可空）
     * @param result     结果 SUCCESS/FAILURE/DENIED/WARN
     * @param errorCode  错误码（可空）
     * @param detailJson 详细信息 JSON（可空）
     */
    public void writeAudit(String action, String moduleCode, String quotaCode,
                           String result, String errorCode, String detailJson) {
        try {
            String tenantId = resolveTenantId();
            String userId = CurrentUserContext.getUserId();
            String traceId = TraceContext.getTraceId();
            LicenseInfo info = current();
            String licenseId = info.licenseId();

            SysLicenseAuditLog auditLog = new SysLicenseAuditLog();
            auditLog.setId(IdGenerator.nextId());
            auditLog.setTenantId(tenantId);
            auditLog.setAction(action);
            auditLog.setLicenseId(licenseId);
            auditLog.setModuleCode(moduleCode);
            auditLog.setQuotaCode(quotaCode);
            auditLog.setResult(result);
            auditLog.setErrorCode(errorCode);
            auditLog.setUserIdHash(hash(userId));
            auditLog.setTenantIdHash(hash(tenantId));
            auditLog.setTraceId(traceId);
            auditLog.setDetailJson(detailJson);
            OffsetDateTime now = OffsetDateTime.now();
            auditLog.setOperatedTime(now);
            auditLog.setCreatedTime(now);
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            // 审计写入失败不阻断业务流程（与 LocalAuditFacade 容错策略一致）
            log.warn("授权审计日志写入失败 action={} moduleCode={} quotaCode={} - {}",
                    action, moduleCode, quotaCode, e.getMessage(), e);
        }
    }

    // ===== 内部辅助方法 =====

    @Override
    public void recordQuotaUsage(String quotaCode, long amount) {
        if (quotaCode == null || quotaCode.isBlank() || amount <= 0) {
            return;
        }
        String tenantId = resolveTenantId();
        String usagePeriod = currentPeriod();
        try {
            LambdaQueryWrapper<SysLicenseUsage> wrapper = new LambdaQueryWrapper<SysLicenseUsage>()
                    .eq(SysLicenseUsage::getTenantId, tenantId)
                    .eq(SysLicenseUsage::getQuotaCode, quotaCode)
                    .eq(SysLicenseUsage::getUsagePeriod, usagePeriod);
            SysLicenseUsage existing = usageMapper.selectOne(wrapper);
            OffsetDateTime now = OffsetDateTime.now();
            if (existing == null) {
                SysLicenseUsage usage = new SysLicenseUsage();
                usage.setId(IdGenerator.nextId());
                usage.setTenantId(tenantId);
                usage.setQuotaCode(quotaCode);
                usage.setUsagePeriod(usagePeriod);
                usage.setUsedAmount(BigDecimal.valueOf(amount));
                usage.setLimitAmount(resolveLimitAsBigDecimal(quotaCode));
                usage.setLastUsedTime(now);
                usageMapper.insert(usage);
            } else {
                BigDecimal newUsed = (existing.getUsedAmount() != null ? existing.getUsedAmount() : BigDecimal.ZERO)
                        .add(BigDecimal.valueOf(amount));
                existing.setUsedAmount(newUsed);
                existing.setLastUsedTime(now);
                usageMapper.updateById(existing);
            }
        } catch (Exception e) {
            // 额度记录失败不阻断业务流程（70 号文档容错策略）
            log.warn("额度使用量记录失败 tenantId={} quotaCode={} amount={} - {}",
                    tenantId, quotaCode, amount, e.getMessage());
        }
    }

    /**
     * 查询当前租户当前周期的额度使用情况列表。
     * 供 LicenseController GET /api/v1/license/usage 调用。
     *
     * @return 额度使用信息列表（quotaCode/usedAmount/limitAmount/usagePeriod/lastUsedTime）
     */
    public List<SysLicenseUsage> listCurrentUsage() {
        String tenantId = resolveTenantId();
        String usagePeriod = currentPeriod();
        try {
            return usageMapper.selectList(
                    new LambdaQueryWrapper<SysLicenseUsage>()
                            .eq(SysLicenseUsage::getTenantId, tenantId)
                            .eq(SysLicenseUsage::getUsagePeriod, usagePeriod)
                            .orderByAsc(SysLicenseUsage::getQuotaCode));
        } catch (Exception e) {
            log.warn("额度使用量查询失败 tenantId={} period={} - {}", tenantId, usagePeriod, e.getMessage());
            return new ArrayList<>();
        }
    }

    // ===== GA2-L174: License 上传 / 刷新 / 审计日志查询 =====

    /**
     * 上传新 License 文件（70 号文档「授权校验流程 - 手动刷新」）。
     *
     * <p>处理流程:
     * <ol>
     *   <li>解析 License JSON 文件</li>
     *   <li>校验必填字段（licenseId/edition/subject/expireTime）+ 版本枚举 + 到期时间</li>
     *   <li>将当前租户的现有 ACTIVE License 状态改为 REVOKED（保留历史）</li>
     *   <li>插入新 ACTIVE License 记录</li>
     *   <li>写审计日志 action=REFRESH result=SUCCESS</li>
     * </ol>
     *
     * <p>第一版不校验签名（v1.1+ 实现公钥验证），不校验 deploymentId（v1.1+ 实现），
     * 不校验 schema 版本兼容性（仅记录字段，v1.1+ 做严格匹配）。
     *
     * @param licenseJson License 文件 JSON 字符串（70 号文档 License 文件结构）
     * @return 上传结果摘要
     * @throws BusinessException LIC-400001 JSON 格式无效 / LIC-400002 必填字段缺失
     */
    @Transactional
    public LicenseUploadResult uploadLicense(String licenseJson) {
        if (licenseJson == null || licenseJson.isBlank()) {
            throw new BusinessException(ErrorCode.LIC_FILE_INVALID, "License 文件内容为空");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(licenseJson);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.LIC_FILE_INVALID, "License 文件不是合法 JSON: " + e.getMessage());
        }

        // 校验必填字段（70 号文档 License 文件结构）
        String licenseId = getTextOrNull(root, "licenseId");
        String edition = getTextOrNull(root, "edition");
        String subject = getTextOrNull(root, "subject");
        String expireTimeStr = getTextOrNull(root, "expireTime");
        if (licenseId == null || edition == null || subject == null || expireTimeStr == null) {
            throw new BusinessException(ErrorCode.LIC_FIELDS_MISSING,
                    "缺少必填字段: licenseId=" + licenseId + " edition=" + edition
                            + " subject=" + subject + " expireTime=" + expireTimeStr);
        }

        // 校验版本枚举（70 号文档版本能力矩阵 4 版本）
        if (!SUPPORTED_EDITIONS.contains(edition)) {
            throw new BusinessException(ErrorCode.LIC_FILE_INVALID, "不支持的版本枚举: " + edition);
        }

        // 解析到期时间
        OffsetDateTime expireTime;
        try {
            expireTime = OffsetDateTime.parse(expireTimeStr);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.LIC_FILE_INVALID, "expireTime 格式无效: " + expireTimeStr);
        }

        // 校验到期时间（已过期的 License 拒绝上传）
        if (expireTime.isBefore(OffsetDateTime.now())) {
            throw new BusinessException(ErrorCode.LIC_FILE_INVALID, "License 已过期: expireTime=" + expireTimeStr);
        }

        // 提取可选字段
        String deploymentId = getTextOrNull(root, "deploymentId");
        String schemaVersion = getTextOrNull(root, "licenseSchemaVersion");
        if (schemaVersion == null) {
            schemaVersion = DEFAULT_SCHEMA_VERSION;
        }
        String signature = getTextOrNull(root, "signature");

        // 提取 limits 和 modules（序列化为 JSON 字符串存入 limits_json / modules_json）
        String limitsJson = root.has("limits") && root.get("limits").isObject() ? root.get("limits").toString() : "{}";
        String modulesJson = root.has("modules") && root.get("modules").isArray() ? root.get("modules").toString() : "[]";

        String tenantId = resolveTenantId();

        // 将现有 ACTIVE License 改为 REVOKED（保留历史，70 号文档「授权变更必须写入审计日志」）
        boolean previousRevoked = revokeCurrentActiveLicense(tenantId);

        // 插入新 ACTIVE License
        SysLicense newLicense = new SysLicense();
        newLicense.setId(IdGenerator.nextId());
        newLicense.setTenantId(tenantId);
        newLicense.setLicenseId(licenseId);
        newLicense.setEdition(edition);
        newLicense.setSubject(subject);
        newLicense.setDeploymentId(deploymentId);
        newLicense.setLicenseSchemaVersion(schemaVersion);
        newLicense.setLimitsJson(limitsJson);
        newLicense.setModulesJson(modulesJson);
        newLicense.setExpireTime(expireTime);
        newLicense.setSignature(signature);
        newLicense.setStatus(ACTIVE_STATUS);
        newLicense.setLastVerifiedTime(OffsetDateTime.now());
        licenseMapper.insert(newLicense);

        // 写审计 action=REFRESH result=SUCCESS（70 号文档「授权变更必须写入审计日志」）
        String detail = String.format(
                "{\"licenseId\":\"%s\",\"edition\":\"%s\",\"subject\":\"%s\",\"previousRevoked\":%b}",
                licenseId, edition, subject, previousRevoked);
        writeAudit("REFRESH", null, null, "SUCCESS", null, detail);

        log.info("License 上传成功: licenseId={} edition={} subject={} previousRevoked={}",
                licenseId, edition, subject, previousRevoked);

        List<String> modules = parseModules(modulesJson);
        return new LicenseUploadResult(licenseId, edition, subject, expireTimeStr, modules, previousRevoked);
    }

    /**
     * 手动刷新 License（70 号文档「授权校验流程 - 手动刷新」）。
     *
     * <p>重新从 sys_license 表读取最新 ACTIVE License，写审计 action=REFRESH。
     * 第一版无内存缓存，refresh 主要用于审计记录管理员手动触发刷新的动作。
     *
     * @throws BusinessException 无（降级 Community 模式也视为刷新成功，仅审计 WARN）
     */
    public void refreshLicense() {
        LicenseInfo info = current();
        if (info.fallbackCommunity()) {
            String detail = "{\"reason\":\"NO_ACTIVE_LICENSE\",\"edition\":\"Community\",\"fallback\":true}";
            log.warn("License 手动刷新: 降级 Community 模式（无 ACTIVE License 记录）");
            writeAudit("REFRESH", null, null, "WARN", null, detail);
        } else {
            String detail = String.format(
                    "{\"licenseId\":\"%s\",\"edition\":\"%s\",\"subject\":\"%s\"}",
                    info.licenseId(), info.edition(), info.subject());
            log.info("License 手动刷新成功: licenseId={} edition={}", info.licenseId(), info.edition());
            writeAudit("REFRESH", null, null, "SUCCESS", null, detail);
        }
    }

    /**
     * GA2-L175: License 定时校验（70 号文档「授权校验流程 - 定时校验」）。
     *
     * <p>由 {@code LicenseVerifyScheduler} 每天 02:00 自动触发，也支持管理员手动通过
     * {@code POST /api/v1/license/verify} 触发（GA2-L175 同步落地）。
     *
     * <p>校验流程:
     * <ol>
     *   <li>调用 {@link #current()} 加载 License（含降级 Community 逻辑）</li>
     *   <li>降级模式 (fallbackCommunity=true) → 写审计 action=VERIFY result=WARN，返回 NO_LICENSE</li>
     *   <li>已过期 (expireTime &lt; now) → 写审计 action=EXPIRE result=WARN，将状态改为 EXPIRED，返回 EXPIRED</li>
     *   <li>即将到期 (剩余天数 &lt; 30 天) → 写审计 action=VERIFY result=WARN，更新 lastVerifiedTime，返回 NEAR_EXPIRY</li>
     *   <li>正常 → 写审计 action=VERIFY result=SUCCESS，更新 lastVerifiedTime，返回 OK</li>
     * </ol>
     *
     * <p>v1.1+ 待落地: 签名验证（RSA 公钥）+ deploymentId 匹配校验 + License Server 远程校验。
     *
     * @return 校验结果（status / licenseId / edition / daysLeft / detail）
     */
    @Transactional
    public LicenseVerifyResult verifyLicense() {
        LicenseInfo info;
        try {
            info = current();
        } catch (Exception e) {
            String detail = "{\"reason\":\"VERIFY_EXCEPTION\",\"error\":\"" + escape(e.getMessage()) + "\"}";
            log.error("[License 定时校验] 加载 License 异常 - {}", e.getMessage(), e);
            writeAudit("VERIFY", null, null, "FAILURE", "LIC-500001", detail);
            return new LicenseVerifyResult("ERROR", null, null, -1L, detail);
        }

        // 降级模式: 无 ACTIVE License
        if (info.fallbackCommunity()) {
            String detail = "{\"reason\":\"NO_ACTIVE_LICENSE\",\"edition\":\"Community\",\"fallback\":true}";
            log.warn("[License 定时校验] 降级 Community 模式: 无 ACTIVE License 记录");
            writeAudit("VERIFY", null, null, "WARN", null, detail);
            return new LicenseVerifyResult("NO_LICENSE", null, "Community", -1L, detail);
        }

        long daysLeft = daysUntilExpiry(info);
        boolean expired = info.expireTime() != null && info.expireTime().isBefore(OffsetDateTime.now());
        boolean nearExpiry = daysLeft >= 0 && daysLeft < EXPIRY_WARNING_DAYS;

        // 已过期: 写 EXPIRE 审计 + 将状态改为 EXPIRED
        if (expired) {
            markLicenseExpired(info.licenseId());
            String detail = String.format(
                    "{\"licenseId\":\"%s\",\"edition\":\"%s\",\"expireTime\":\"%s\",\"daysLeft\":%d,\"action\":\"MARK_EXPIRED\"}",
                    info.licenseId(), info.edition(), info.expireTime(), daysLeft);
            log.error("[License 定时校验] License 已过期: licenseId={} edition={} expireTime={} - 已标记 EXPIRED 状态",
                    info.licenseId(), info.edition(), info.expireTime());
            writeAudit("EXPIRE", null, null, "WARN", "LIC-403001", detail);
            return new LicenseVerifyResult("EXPIRED", info.licenseId(), info.edition(), daysLeft, detail);
        }

        // 更新 last_verified_time（70 号文档「篡改检测和定时校验」）
        updateLastVerifiedTime(info.licenseId());

        // 即将到期
        if (nearExpiry) {
            String detail = buildVerifyDetail(info, daysLeft, "NEAR_EXPIRY");
            log.warn("[License 定时校验] License 即将到期: licenseId={} edition={} 剩余 {} 天",
                    info.licenseId(), info.edition(), daysLeft);
            writeAudit("VERIFY", null, null, "WARN", null, detail);
            return new LicenseVerifyResult("NEAR_EXPIRY", info.licenseId(), info.edition(), daysLeft, detail);
        }

        // 正常
        String detail = buildVerifyDetail(info, daysLeft, "OK");
        log.info("[License 定时校验] 校验通过: licenseId={} edition={} subject={} 剩余 {} 天",
                info.licenseId(), info.edition(), info.subject(), daysLeft);
        writeAudit("VERIFY", null, null, "SUCCESS", null, detail);
        return new LicenseVerifyResult("OK", info.licenseId(), info.edition(), daysLeft, detail);
    }

    /** 计算剩余到期天数，expireTime 为 null 时返回 -1（无到期限制） */
    private long daysUntilExpiry(LicenseInfo info) {
        if (info.expireTime() == null) {
            return -1L;
        }
        return OffsetDateTime.now().until(info.expireTime(), ChronoUnit.DAYS);
    }

    /** 构建定时校验详情 JSON */
    private String buildVerifyDetail(LicenseInfo info, long daysLeft, String status) {
        return String.format(
                "{\"licenseId\":\"%s\",\"edition\":\"%s\",\"subject\":\"%s\",\"modules\":%s,\"expireTime\":\"%s\",\"daysLeft\":%d,\"status\":\"%s\",\"fallback\":false}",
                info.licenseId(),
                info.edition(),
                escape(info.subject()),
                toJsonArray(info.modules()),
                info.expireTime(),
                daysLeft,
                status);
    }

    /** 将 List<String> 转换为合法 JSON 数组字符串 */
    private static String toJsonArray(List<String> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(escape(items.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    /** 简单 JSON 字符串转义 */
    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    /** 将指定 licenseId 的 ACTIVE License 状态改为 EXPIRED */
    private void markLicenseExpired(String licenseId) {
        try {
            String tenantId = resolveTenantId();
            LambdaQueryWrapper<SysLicense> wrapper = new LambdaQueryWrapper<SysLicense>()
                    .eq(SysLicense::getTenantId, tenantId)
                    .eq(SysLicense::getLicenseId, licenseId)
                    .eq(SysLicense::getStatus, ACTIVE_STATUS);
            SysLicense entity = licenseMapper.selectOne(wrapper);
            if (entity != null) {
                entity.setStatus(EXPIRED_STATUS);
                entity.setLastVerifiedTime(OffsetDateTime.now());
                licenseMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.warn("标记 License EXPIRED 失败 licenseId={} - {}", licenseId, e.getMessage());
        }
    }

    /** 更新 License 的 last_verified_time */
    private void updateLastVerifiedTime(String licenseId) {
        try {
            String tenantId = resolveTenantId();
            LambdaQueryWrapper<SysLicense> wrapper = new LambdaQueryWrapper<SysLicense>()
                    .eq(SysLicense::getTenantId, tenantId)
                    .eq(SysLicense::getLicenseId, licenseId)
                    .eq(SysLicense::getStatus, ACTIVE_STATUS);
            SysLicense entity = licenseMapper.selectOne(wrapper);
            if (entity != null) {
                entity.setLastVerifiedTime(OffsetDateTime.now());
                licenseMapper.updateById(entity);
            }
        } catch (Exception e) {
            log.warn("更新 last_verified_time 失败 licenseId={} - {}", licenseId, e.getMessage());
        }
    }

    /** License 定时校验结果 */
    public record LicenseVerifyResult(
            /** 校验状态: OK / NEAR_EXPIRY / EXPIRED / NO_LICENSE / ERROR */
            String status,
            /** License ID（降级模式或异常时为 null） */
            String licenseId,
            /** 版本 */
            String edition,
            /** 剩余天数（无到期限制或异常时为 -1） */
            long daysLeft,
            /** 详情 JSON */
            String detail
    ) {}

    /**
     * 分页查询授权审计日志（70 号文档「授权运行数据模型 - sys_license_audit_log」管理端观察接口）。
     *
     * @param page       页码（1-based）
     * @param size       每页大小
     * @param action     动作过滤（LOAD/VERIFY/REFRESH/DENY/EXPIRE/QUOTA_EXCEEDED，可空）
     * @param licenseId  授权 ID 过滤（可空）
     * @param moduleCode 模块编码过滤（可空）
     * @return 分页审计日志列表（按操作时间倒序）
     */
    public Page<LicenseAuditLogVo> listAuditLogs(int page, int size, String action, String licenseId, String moduleCode) {
        String tenantId = resolveTenantId();
        LambdaQueryWrapper<SysLicenseAuditLog> wrapper = new LambdaQueryWrapper<SysLicenseAuditLog>()
                .eq(SysLicenseAuditLog::getTenantId, tenantId)
                .orderByDesc(SysLicenseAuditLog::getOperatedTime);
        if (action != null && !action.isBlank()) {
            wrapper.eq(SysLicenseAuditLog::getAction, action);
        }
        if (licenseId != null && !licenseId.isBlank()) {
            wrapper.eq(SysLicenseAuditLog::getLicenseId, licenseId);
        }
        if (moduleCode != null && !moduleCode.isBlank()) {
            wrapper.eq(SysLicenseAuditLog::getModuleCode, moduleCode);
        }
        Page<SysLicenseAuditLog> pageResult = auditLogMapper.selectPage(new Page<>(page, size), wrapper);
        Page<LicenseAuditLogVo> result = new Page<>(page, size, pageResult.getTotal());
        List<LicenseAuditLogVo> records = new ArrayList<>(pageResult.getRecords().size());
        for (SysLicenseAuditLog entry : pageResult.getRecords()) {
            records.add(new LicenseAuditLogVo(
                    entry.getAction(),
                    entry.getLicenseId(),
                    entry.getModuleCode(),
                    entry.getQuotaCode(),
                    entry.getResult(),
                    entry.getErrorCode(),
                    entry.getUserIdHash(),
                    entry.getTraceId(),
                    entry.getDetailJson(),
                    entry.getOperatedTime()));
        }
        result.setRecords(records);
        return result;
    }

    /** 将当前租户的现有 ACTIVE License 改为 REVOKED（保留历史）。返回是否撤销了旧 License。 */
    private boolean revokeCurrentActiveLicense(String tenantId) {
        LambdaQueryWrapper<SysLicense> wrapper = new LambdaQueryWrapper<SysLicense>()
                .eq(SysLicense::getTenantId, tenantId)
                .eq(SysLicense::getStatus, ACTIVE_STATUS);
        List<SysLicense> actives = licenseMapper.selectList(wrapper);
        for (SysLicense old : actives) {
            old.setStatus(REVOKED_STATUS);
            licenseMapper.updateById(old);
        }
        return !actives.isEmpty();
    }

    /** 从 JsonNode 提取文本字段，不存在或 null 时返回 null */
    private static String getTextOrNull(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            return node.get(field).asText();
        }
        return null;
    }

    /** 解析额度上限为 BigDecimal（供 recordQuotaUsage 填充 limit_amount） */
    private BigDecimal resolveLimitAsBigDecimal(String quotaCode) {
        LicenseInfo info = current();
        Long limit = extractLimit(info.limitsJson(), quotaCode);
        return limit != null ? BigDecimal.valueOf(limit) : null;
    }

    private LicenseInfo toLicenseInfo(SysLicense entity) {
        List<String> modules = parseModules(entity.getModulesJson());
        return new LicenseInfo(
                entity.getLicenseId(),
                entity.getEdition(),
                entity.getSubject(),
                entity.getDeploymentId(),
                entity.getLicenseSchemaVersion() != null ? entity.getLicenseSchemaVersion() : DEFAULT_SCHEMA_VERSION,
                entity.getExpireTime(),
                entity.getStatus(),
                modules,
                entity.getLimitsJson(),
                entity.getSignature(),
                false
        );
    }

    private LicenseInfo fallbackCommunity() {
        return new LicenseInfo(
                null,
                "Community",
                "Community Default",
                null,
                DEFAULT_SCHEMA_VERSION,
                null,
                ACTIVE_STATUS,
                FALLBACK_COMMUNITY_MODULES,
                FALLBACK_COMMUNITY_LIMITS_JSON,
                null,
                true
        );
    }

    private List<String> parseModules(String modulesJson) {
        if (modulesJson == null || modulesJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(modulesJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("modulesJson 解析失败 {} - {}", modulesJson, e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 limitsJson 中提取指定 quotaCode 的额度上限。
     * 支持 quotaCode 直接作为 key（如 "ai.monthly.tokens": 1000000），
     * 也兼容 70 号文档示例的 camelCase key（如 "aiMonthlyQuota": 1000000）。
     *
     * @return 额度上限，null 表示无限制
     */
    private Long extractLimit(String limitsJson, String quotaCode) {
        if (limitsJson == null || limitsJson.isBlank() || "{}".equals(limitsJson)) {
            return null;
        }
        try {
            Map<String, Object> limits = objectMapper.readValue(limitsJson, new TypeReference<Map<String, Object>>() {});
            // 1. 直接匹配 quotaCode
            Object value = limits.get(quotaCode);
            // 2. 兼容 camelCase（如 ai.monthly.tokens -> aiMonthlyTokens）
            if (value == null) {
                String camelKey = toCamelCase(quotaCode);
                value = limits.get(camelKey);
            }
            if (value == null) {
                return null;
            }
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            log.warn("limitsJson 解析失败 quotaCode={} limitsJson={} - {}", quotaCode, limitsJson, e.getMessage());
            return null;
        }
    }

    /** 将点分表示法转为 camelCase（如 ai.monthly.tokens -> aiMonthlyTokens） */
    private static String toCamelCase(String dotNotation) {
        if (dotNotation == null || dotNotation.isEmpty()) {
            return dotNotation;
        }
        String[] parts = dotNotation.split("\\.");
        if (parts.length == 1) {
            return parts[0];
        }
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)))
                  .append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    private String resolveTenantId() {
        String tenantId = CurrentUserContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            return DEFAULT_TENANT_ID;
        }
        return tenantId;
    }

    /** 当前统计周期（按租户时区 Asia/Shanghai，格式 yyyy-MM） */
    private String currentPeriod() {
        return OffsetDateTime.now(TENANT_TIMEZONE).format(PERIOD_FORMATTER);
    }

    /** SHA-256 哈希（hex，64 字符），用于审计日志脱敏 */
    private static String hash(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 标准算法，理论上不会抛出
            return "hash-error:" + input.hashCode();
        }
    }
}
