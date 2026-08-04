package com.yutong.auth.emergency;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.auth.domain.AuthEmergencyAdmin;
import com.yutong.auth.mapper.AuthEmergencyAdminMapper;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.id.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * 应急管理员服务。设计来源: 32-企业级权限与租户接入方案、21-安全与质量设计 A02 加密失败
 * 
 * <p>生产环境必须准备最少两个受控应急管理员身份，凭据放入外部 Secret 管理，
 * 默认禁用或强审计，仅在 IdP 故障和明确审批后启用。
 * 
 * <p>每次启用必须记录原因、批准人、开始/结束时间、操作范围和事后复核；
 * 禁止把开发管理员账号当作应急账号。
 *
 * <p>凭据安全约束 (21 号文档 A02):
 * <ul>
 *   <li>secret_hash 只允许存放 BCrypt 摘要 ({@code $2a$/$2b$/$2y$} 前缀)，任何环节不落明文</li>
 *   <li>校验只走 {@link BCryptPasswordEncoder#matches}，不做明文回退比较</li>
 *   <li>历史明文记录一律判定为无效凭据，必须由运维走 {@link #resetSecret} 重置</li>
 * </ul>
 */
@Slf4j
@Service
public class EmergencyAdminService {

    /** BCrypt 强度。应急凭据属于高价值凭据，使用高于默认(10)的 cost。 */
    private static final int BCRYPT_STRENGTH = 12;

    /** 应急密钥最小长度，避免弱口令。 */
    private static final int MIN_SECRET_LENGTH = 12;

    /** BCrypt 摘要前缀，用于识别历史明文/非法摘要。 */
    private static final String BCRYPT_PREFIX = "$2";

    private final AuthEmergencyAdminMapper adminMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public EmergencyAdminService(AuthEmergencyAdminMapper adminMapper) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    /**
     * 启用应急管理员
     * 
     * @param tenantId       租户 ID
     * @param adminCode      管理员编码
     * @param approvedBy     批准人
     * @param approvedReason 批准原因
     * @return 是否启用成功
     */
    public boolean enable(String tenantId, String adminCode, String approvedBy, String approvedReason) {
        AuthEmergencyAdmin admin = loadAdmin(tenantId, adminCode);
        if (admin == null) {
            log.warn("应急管理员不存在: tenantId={}, adminCode={}", tenantId, adminCode);
            return false;
        }

        if (!AuthEmergencyAdmin.STATUS_DISABLED.equals(admin.getStatus())) {
            log.warn("应急管理员状态不是 DISABLED，无法启用: adminCode={}, status={}", 
                    adminCode, admin.getStatus());
            return false;
        }

        admin.setStatus(AuthEmergencyAdmin.STATUS_ENABLED);
        admin.setApprovedBy(approvedBy);
        admin.setApprovedReason(approvedReason);
        admin.setApprovedAt(OffsetDateTime.now());
        admin.setUpdatedBy(approvedBy);
        admin.setUpdatedTime(OffsetDateTime.now());

        int rows = adminMapper.updateById(admin);
        if (rows > 0) {
            log.info("应急管理员已启用: tenantId={}, adminCode={}, approvedBy={}", 
                    tenantId, adminCode, approvedBy);
            return true;
        }
        return false;
    }

    /**
     * 禁用应急管理员
     * 
     * @param tenantId  租户 ID
     * @param adminCode 管理员编码
     * @param operator  操作人
     * @return 是否禁用成功
     */
    public boolean disable(String tenantId, String adminCode, String operator) {
        AuthEmergencyAdmin admin = loadAdmin(tenantId, adminCode);
        if (admin == null) {
            log.warn("应急管理员不存在: tenantId={}, adminCode={}", tenantId, adminCode);
            return false;
        }

        admin.setStatus(AuthEmergencyAdmin.STATUS_DISABLED);
        admin.setUpdatedBy(operator);
        admin.setUpdatedTime(OffsetDateTime.now());

        int rows = adminMapper.updateById(admin);
        if (rows > 0) {
            log.info("应急管理员已禁用: tenantId={}, adminCode={}, operator={}", 
                    tenantId, adminCode, operator);
            return true;
        }
        return false;
    }

    /**
     * 使用应急管理员登录
     *
     * <p>密钥校验只走 BCrypt {@code matches}，不接受任何明文回退比较；
     * 若 secret_hash 不是 BCrypt 摘要（历史明文数据），一律判定校验失败并要求重置。
     *
     * @param tenantId   租户 ID
     * @param adminCode  管理员编码
     * @param secret     密钥（明文，仅在内存中参与 BCrypt 校验，不落库不打印）
     * @return 是否使用成功
     */
    public boolean use(String tenantId, String adminCode, String secret) {
        if (secret == null || secret.isEmpty()) {
            log.warn("应急管理员密钥为空: tenantId={}, adminCode={}", tenantId, adminCode);
            return false;
        }

        AuthEmergencyAdmin admin = loadAdmin(tenantId, adminCode);
        if (admin == null) {
            log.warn("应急管理员不存在: tenantId={}, adminCode={}", tenantId, adminCode);
            return false;
        }

        if (!AuthEmergencyAdmin.STATUS_ENABLED.equals(admin.getStatus())) {
            log.warn("应急管理员未启用，无法使用: adminCode={}, status={}", 
                    adminCode, admin.getStatus());
            return false;
        }

        int usageCount = admin.getUsageCount() != null ? admin.getUsageCount() : 0;
        int maxUsageCount = admin.getMaxUsageCount() != null ? admin.getMaxUsageCount() : 0;
        if (usageCount >= maxUsageCount) {
            log.warn("应急管理员使用次数已达上限: adminCode={}, usageCount={}, maxUsageCount={}", 
                    adminCode, usageCount, maxUsageCount);
            return false;
        }

        if (!isBcryptHash(admin.getSecretHash())) {
            // 21 号文档 A02: 明文/非 BCrypt 凭据视为不可用，必须走 resetSecret 重置为 BCrypt
            log.error("应急管理员凭据未使用 BCrypt 存储，已拒绝使用并要求重置: tenantId={}, adminCode={}",
                    tenantId, adminCode);
            return false;
        }

        if (!passwordEncoder.matches(secret, admin.getSecretHash())) {
            log.warn("应急管理员密钥错误: adminCode={}", adminCode);
            return false;
        }

        admin.setStatus(AuthEmergencyAdmin.STATUS_IN_USE);
        admin.setLastUsedAt(OffsetDateTime.now());
        admin.setUsageCount(usageCount + 1);
        admin.setUpdatedTime(OffsetDateTime.now());

        int rows = adminMapper.updateById(admin);
        if (rows > 0) {
            log.info("应急管理员已使用: tenantId={}, adminCode={}, usageCount={}", 
                    tenantId, adminCode, admin.getUsageCount());
            return true;
        }
        return false;
    }

    /**
     * 审计应急管理员使用情况
     * 
     * @param tenantId 租户 ID
     * @return 审计报告
     */
    public String audit(String tenantId) {
        LambdaQueryWrapper<AuthEmergencyAdmin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthEmergencyAdmin::getTenantId, tenantId);
        wrapper.eq(AuthEmergencyAdmin::getDeleted, false);

        long count = adminMapper.selectCount(wrapper);
        long enabledCount = adminMapper.selectCount(
                wrapper.clone().eq(AuthEmergencyAdmin::getStatus, AuthEmergencyAdmin.STATUS_ENABLED));
        long inUseCount = adminMapper.selectCount(
                wrapper.clone().eq(AuthEmergencyAdmin::getStatus, AuthEmergencyAdmin.STATUS_IN_USE));

        String report = String.format("应急管理员审计: tenantId=%s, total=%d, enabled=%d, inUse=%d",
                tenantId, count, enabledCount, inUseCount);
        log.info(report);
        return report;
    }

    /**
     * 加载应急管理员
     * 
     * @param tenantId  租户 ID
     * @param adminCode 管理员编码
     * @return 应急管理员
     */
    private AuthEmergencyAdmin loadAdmin(String tenantId, String adminCode) {
        LambdaQueryWrapper<AuthEmergencyAdmin> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthEmergencyAdmin::getTenantId, tenantId);
        wrapper.eq(AuthEmergencyAdmin::getAdminCode, adminCode);
        wrapper.eq(AuthEmergencyAdmin::getDeleted, false);
        return adminMapper.selectOne(wrapper);
    }

    /**
     * 重置应急管理员密钥（运维通道）。
     *
     * <p>用于历史明文凭据迁移与定期轮换: 新密钥一律以 BCrypt 摘要写回，
     * 同时把状态复位为 DISABLED（需重新审批启用），避免重置后被直接使用。
     *
     * @param tenantId  租户 ID
     * @param adminCode 管理员编码
     * @param rawSecret 新明文密钥（内部自动 BCrypt encode，不落库不打印）
     * @param operator  操作人
     * @return 是否重置成功
     */
    public boolean resetSecret(String tenantId, String adminCode, String rawSecret, String operator) {
        validateSecretStrength(rawSecret);
        AuthEmergencyAdmin admin = loadAdmin(tenantId, adminCode);
        if (admin == null) {
            log.warn("应急管理员不存在: tenantId={}, adminCode={}", tenantId, adminCode);
            return false;
        }

        admin.setSecretHash(passwordEncoder.encode(rawSecret));
        admin.setStatus(AuthEmergencyAdmin.STATUS_DISABLED);
        admin.setUsageCount(0);
        admin.setUpdatedBy(operator);
        admin.setUpdatedTime(OffsetDateTime.now());

        int rows = adminMapper.updateById(admin);
        if (rows > 0) {
            log.info("应急管理员密钥已重置为 BCrypt 并复位为 DISABLED: tenantId={}, adminCode={}, operator={}",
                    tenantId, adminCode, operator);
            return true;
        }
        return false;
    }

    /**
     * 创建应急管理员（仅用于初始化）
     *
     * @param tenantId    租户 ID
     * @param adminCode   管理员编码
     * @param adminName   管理员名称
     * @param rawPassword 明文密钥（内部自动 BCrypt encode，不落库不打印）
     * @return 应急管理员 ID
     */
    public String create(String tenantId, String adminCode, String adminName, String rawPassword) {
        validateSecretStrength(rawPassword);
        AuthEmergencyAdmin admin = new AuthEmergencyAdmin();
        admin.setId(IdGenerator.nextId());
        admin.setTenantId(tenantId);
        admin.setAdminCode(adminCode);
        admin.setAdminName(adminName);
        admin.setSecretHash(passwordEncoder.encode(rawPassword));
        admin.setStatus(AuthEmergencyAdmin.STATUS_DISABLED);
        admin.setUsageCount(0);
        admin.setMaxUsageCount(5);
        admin.setCreatedBy("system");
        admin.setCreatedTime(OffsetDateTime.now());
        admin.setUpdatedBy("system");
        admin.setUpdatedTime(OffsetDateTime.now());
        admin.setDeleted(false);
        admin.setVersion(0);

        adminMapper.insert(admin);
        log.info("应急管理员已创建: tenantId={}, adminCode={}", tenantId, adminCode);
        return admin.getId();
    }

    /** 判断 secret_hash 是否为 BCrypt 摘要（$2a$/$2b$/$2y$ 前缀）。 */
    private boolean isBcryptHash(String secretHash) {
        return secretHash != null && secretHash.startsWith(BCRYPT_PREFIX);
    }

    /** 校验明文密钥强度，拒绝空值与短口令。 */
    private void validateSecretStrength(String rawSecret) {
        if (rawSecret == null || rawSecret.isBlank()) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID, "应急管理员密钥不能为空");
        }
        if (rawSecret.length() < MIN_SECRET_LENGTH) {
            throw new BusinessException(ErrorCode.SYS_PARAM_INVALID,
                    "应急管理员密钥长度不足，至少 " + MIN_SECRET_LENGTH + " 位");
        }
    }
}
