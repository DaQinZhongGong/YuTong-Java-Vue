package com.yutong.auth.refresh;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yutong.auth.domain.AuthRefreshTokenFamily;
import com.yutong.auth.mapper.AuthRefreshTokenFamilyMapper;
import com.yutong.common.id.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;

/**
 * Refresh Token 轮换服务。设计来源: 32-企业级权限与租户接入方案
 * 
 * <p>实现 refresh-token rotation：每次成功刷新都签发新 token pair 并撤销旧 refresh token；
 * 检测到旧 token 重用时撤销整个 token family、记录高危登录审计并要求重新登录。
 * 
 * <p>refresh token 本体不得落库或写日志，只保存带应用 pepper 的哈希。
 * Redis 数据丢失时所有 refresh 会话失效并要求重新登录，不得跳过校验。
 */
@Slf4j
@Service
public class RefreshTokenRotationService {

    private final AuthRefreshTokenFamilyMapper familyMapper;

    public RefreshTokenRotationService(AuthRefreshTokenFamilyMapper familyMapper) {
        this.familyMapper = familyMapper;
    }

    /**
     * 签发新的 refresh token family
     * 
     * @param tenantId   租户 ID
     * @param sessionId  会话 ID
     * @param subjectId  主体 ID（用户 ID）
     * @param deviceHash 设备指纹哈希
     * @param ttlSeconds 有效期（秒）
     * @return family ID
     */
    public String issue(String tenantId, String sessionId, String subjectId, String deviceHash, long ttlSeconds) {
        String familyId = IdGenerator.nextId();
        String jti = IdGenerator.nextId();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusSeconds(ttlSeconds);

        AuthRefreshTokenFamily family = new AuthRefreshTokenFamily();
        family.setId(IdGenerator.nextId());
        family.setTenantId(tenantId);
        family.setFamilyId(familyId);
        family.setSessionId(sessionId);
        family.setSubjectId(subjectId);
        family.setCurrentJti(jti);
        family.setDeviceHash(deviceHash);
        family.setIssuedAt(now);
        family.setExpiresAt(expiresAt);
        family.setRotatedAt(now);
        family.setRevoked(false);
        family.setCreatedBy("system");
        family.setCreatedTime(now);
        family.setUpdatedBy("system");
        family.setUpdatedTime(now);
        family.setDeleted(false);
        family.setVersion(0);

        familyMapper.insert(family);
        log.info("Refresh token family 已签发: familyId={}, sessionId={}, subjectId={}, expiresAt={}",
                familyId, sessionId, subjectId, expiresAt);
        return familyId;
    }

    /**
     * 轮换 refresh token
     * 
     * <p>每次成功刷新都签发新 token pair 并撤销旧 refresh token。
     * 
     * @param familyId  family ID
     * @param oldJti    旧 token 的 jti
     * @param ttlSeconds 新 token 有效期（秒）
     * @return 新 token 的 jti，如果轮换失败返回 null
     */
    public String rotate(String familyId, String oldJti, long ttlSeconds) {
        AuthRefreshTokenFamily family = loadByFamilyId(familyId);
        if (family == null) {
            log.warn("Refresh token family 不存在: familyId={}", familyId);
            return null;
        }

        // 检查是否已撤销
        if (Boolean.TRUE.equals(family.getRevoked())) {
            log.warn("Refresh token family 已撤销: familyId={}, reason={}", familyId, family.getRevokeReason());
            return null;
        }

        // 检查是否过期
        if (family.getExpiresAt().isBefore(OffsetDateTime.now())) {
            log.warn("Refresh token family 已过期: familyId={}, expiresAt={}", familyId, family.getExpiresAt());
            revokeFamily(familyId, "EXPIRED");
            return null;
        }

        // 重放检测：如果 oldJti 不等于 currentJti，说明检测到重放
        if (!oldJti.equals(family.getCurrentJti())) {
            log.error("检测到 refresh token 重放攻击: familyId={}, oldJti={}, currentJti={}",
                    familyId, oldJti, family.getCurrentJti());
            revokeFamily(familyId, "REPLAY_DETECTED");
            return null;
        }

        // 签发新 jti
        String newJti = IdGenerator.nextId();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime newExpiresAt = now.plusSeconds(ttlSeconds);

        family.setCurrentJti(newJti);
        family.setRotatedAt(now);
        family.setExpiresAt(newExpiresAt);
        family.setUpdatedTime(now);

        familyMapper.updateById(family);
        log.info("Refresh token 已轮换: familyId={}, oldJti={}, newJti={}, newExpiresAt={}",
                familyId, oldJti, newJti, newExpiresAt);
        return newJti;
    }

    /**
     * 撤销 refresh token family
     * 
     * @param familyId family ID
     * @param reason   撤销原因
     * @return 是否撤销成功
     */
    public boolean revoke(String familyId, String reason) {
        return revokeFamily(familyId, reason);
    }

    /**
     * 撤销整个 token family（检测到重放或主动登出时调用）
     * 
     * @param familyId family ID
     * @param reason   撤销原因
     * @return 是否撤销成功
     */
    private boolean revokeFamily(String familyId, String reason) {
        AuthRefreshTokenFamily family = loadByFamilyId(familyId);
        if (family == null) {
            log.warn("Refresh token family 不存在: familyId={}", familyId);
            return false;
        }

        family.setRevoked(true);
        family.setRevokeReason(reason);
        family.setUpdatedTime(OffsetDateTime.now());

        int rows = familyMapper.updateById(family);
        if (rows > 0) {
            log.info("Refresh token family 已撤销: familyId={}, reason={}", familyId, reason);
            return true;
        }
        return false;
    }

    /**
     * 根据 sessionId 撤销所有 token family（强制登出时调用）
     * 
     * @param sessionId 会话 ID
     * @param reason    撤销原因
     * @return 撤销的数量
     */
    public int revokeBySession(String sessionId, String reason) {
        LambdaQueryWrapper<AuthRefreshTokenFamily> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthRefreshTokenFamily::getSessionId, sessionId);
        wrapper.eq(AuthRefreshTokenFamily::getDeleted, false);
        wrapper.eq(AuthRefreshTokenFamily::getRevoked, false);

        int count = 0;
        for (AuthRefreshTokenFamily family : familyMapper.selectList(wrapper)) {
            if (revokeFamily(family.getFamilyId(), reason)) {
                count++;
            }
        }
        log.info("根据 sessionId 撤销 token family: sessionId={}, count={}", sessionId, count);
        return count;
    }

    /**
     * 根据 subjectId 撤销所有 token family（账号停用时调用）
     * 
     * @param subjectId 主体 ID
     * @param reason    撤销原因
     * @return 撤销的数量
     */
    public int revokeBySubject(String subjectId, String reason) {
        LambdaQueryWrapper<AuthRefreshTokenFamily> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthRefreshTokenFamily::getSubjectId, subjectId);
        wrapper.eq(AuthRefreshTokenFamily::getDeleted, false);
        wrapper.eq(AuthRefreshTokenFamily::getRevoked, false);

        int count = 0;
        for (AuthRefreshTokenFamily family : familyMapper.selectList(wrapper)) {
            if (revokeFamily(family.getFamilyId(), reason)) {
                count++;
            }
        }
        log.info("根据 subjectId 撤销 token family: subjectId={}, count={}", subjectId, count);
        return count;
    }

    /**
     * 清理过期的 token family
     * 
     * @return 清理的数量
     */
    public int cleanupExpired() {
        LambdaQueryWrapper<AuthRefreshTokenFamily> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(AuthRefreshTokenFamily::getExpiresAt, OffsetDateTime.now());
        wrapper.eq(AuthRefreshTokenFamily::getDeleted, false);

        int count = 0;
        for (AuthRefreshTokenFamily family : familyMapper.selectList(wrapper)) {
            family.setDeleted(true);
            family.setUpdatedTime(OffsetDateTime.now());
            familyMapper.updateById(family);
            count++;
        }
        log.info("清理过期 token family: count={}", count);
        return count;
    }

    /**
     * 根据 family ID 加载
     * 
     * @param familyId family ID
     * @return token family
     */
    private AuthRefreshTokenFamily loadByFamilyId(String familyId) {
        LambdaQueryWrapper<AuthRefreshTokenFamily> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthRefreshTokenFamily::getFamilyId, familyId);
        wrapper.eq(AuthRefreshTokenFamily::getDeleted, false);
        return familyMapper.selectOne(wrapper);
    }
}
