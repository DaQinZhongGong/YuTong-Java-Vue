package com.yutong.auth.loginlog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.domain.AuthLoginLog;
import com.yutong.auth.mapper.AuthLoginLogMapper;
import com.yutong.common.id.IdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 登录日志服务。设计来源: 32-企业级权限与租户接入方案
 * 
 * <p>记录所有登录、登出、失败事件。不得混写为普通业务操作日志。
 * 登录、轮换、重放和撤销摘要写 auth_login_log。
 */
@Slf4j
@Service
public class LoginLogService {

    private final AuthLoginLogMapper logMapper;

    public LoginLogService(AuthLoginLogMapper logMapper) {
        this.logMapper = logMapper;
    }

    /**
     * 记录登录成功
     * 
     * @param tenantId   租户 ID
     * @param userId     用户 ID
     * @param username   用户名
     * @param authMethod 认证方式：OIDC/LDAP_AD/CAS/MOCK/EMERGENCY_ADMIN
     * @param ipAddress  客户端 IP
     * @param userAgent  User-Agent
     * @param sessionId  会话 ID
     * @param traceId    链路追踪 ID
     */
    public void logSuccess(String tenantId, String userId, String username, String authMethod,
                           String ipAddress, String userAgent, String sessionId, String traceId) {
        AuthLoginLog loginLog = buildLog(tenantId, userId, username, authMethod,
                AuthLoginLog.RESULT_SUCCESS, ipAddress, userAgent, sessionId, traceId, null);
        logMapper.insert(loginLog);
        log.info("登录成功: tenantId={}, userId={}, username={}, authMethod={}, sessionId={}",
                tenantId, userId, username, authMethod, sessionId);
    }

    /**
     * 记录登录失败
     * 
     * @param tenantId      租户 ID
     * @param username      用户名（可为空）
     * @param authMethod    认证方式
     * @param ipAddress     客户端 IP
     * @param userAgent     User-Agent
     * @param traceId       链路追踪 ID
     * @param failureReason 失败原因
     */
    public void logFailed(String tenantId, String username, String authMethod,
                          String ipAddress, String userAgent, String traceId, String failureReason) {
        AuthLoginLog loginLog = buildLog(tenantId, null, username, authMethod,
                AuthLoginLog.RESULT_FAILED, ipAddress, userAgent, null, traceId, failureReason);
        logMapper.insert(loginLog);
        log.warn("登录失败: tenantId={}, username={}, authMethod={}, reason={}",
                tenantId, username, authMethod, failureReason);
    }

    /**
     * 记录账号锁定
     * 
     * @param tenantId      租户 ID
     * @param username      用户名
     * @param authMethod    认证方式
     * @param ipAddress     客户端 IP
     * @param userAgent     User-Agent
     * @param traceId       链路追踪 ID
     * @param failureReason 锁定原因
     */
    public void logLocked(String tenantId, String username, String authMethod,
                          String ipAddress, String userAgent, String traceId, String failureReason) {
        AuthLoginLog loginLog = buildLog(tenantId, null, username, authMethod,
                AuthLoginLog.RESULT_LOCKED, ipAddress, userAgent, null, traceId, failureReason);
        logMapper.insert(loginLog);
        log.warn("账号锁定: tenantId={}, username={}, authMethod={}, reason={}",
                tenantId, username, authMethod, failureReason);
    }

    /**
     * 查询登录日志（按用户 ID）
     * 
     * @param tenantId 租户 ID
     * @param userId   用户 ID
     * @param limit    限制条数
     * @return 登录日志列表
     */
    public List<AuthLoginLog> queryByUserId(String tenantId, String userId, int limit) {
        LambdaQueryWrapper<AuthLoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthLoginLog::getTenantId, tenantId);
        wrapper.eq(AuthLoginLog::getUserId, userId);
        wrapper.eq(AuthLoginLog::getDeleted, false);
        wrapper.orderByDesc(AuthLoginLog::getCreatedTime);
        wrapper.last("LIMIT " + limit);
        return logMapper.selectList(wrapper);
    }

    /**
     * 查询登录日志（按会话 ID）
     * 
     * @param tenantId  租户 ID
     * @param sessionId 会话 ID
     * @return 登录日志列表
     */
    public List<AuthLoginLog> queryBySessionId(String tenantId, String sessionId) {
        LambdaQueryWrapper<AuthLoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthLoginLog::getTenantId, tenantId);
        wrapper.eq(AuthLoginLog::getSessionId, sessionId);
        wrapper.eq(AuthLoginLog::getDeleted, false);
        wrapper.orderByDesc(AuthLoginLog::getCreatedTime);
        return logMapper.selectList(wrapper);
    }

    /**
     * 分页查询登录日志
     * 
     * @param tenantId   租户 ID
     * @param username   用户名（模糊匹配）
     * @param authMethod 认证方式
     * @param result     登录结果
     * @param page       页码
     * @param size       每页大小
     * @return 分页结果
     */
    public Page<AuthLoginLog> queryPage(String tenantId, String username, String authMethod,
                                         String result, int page, int size) {
        LambdaQueryWrapper<AuthLoginLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AuthLoginLog::getTenantId, tenantId);
        wrapper.eq(AuthLoginLog::getDeleted, false);

        if (username != null && !username.isBlank()) {
            wrapper.like(AuthLoginLog::getUsername, username);
        }
        if (authMethod != null && !authMethod.isBlank()) {
            wrapper.eq(AuthLoginLog::getAuthMethod, authMethod);
        }
        if (result != null && !result.isBlank()) {
            wrapper.eq(AuthLoginLog::getLoginResult, result);
        }

        wrapper.orderByDesc(AuthLoginLog::getCreatedTime);
        return logMapper.selectPage(new Page<>(page, size), wrapper);
    }

    /**
     * 构造登录日志
     */
    private AuthLoginLog buildLog(String tenantId, String userId, String username, String authMethod,
                                   String loginResult, String ipAddress, String userAgent,
                                   String sessionId, String traceId, String failureReason) {
        AuthLoginLog loginLog = new AuthLoginLog();
        loginLog.setId(IdGenerator.nextId());
        loginLog.setTenantId(tenantId);
        loginLog.setUserId(userId);
        loginLog.setUsername(username);
        loginLog.setAuthMethod(authMethod);
        loginLog.setLoginResult(loginResult);
        loginLog.setIpAddress(ipAddress);
        loginLog.setUserAgent(userAgent);
        loginLog.setSessionId(sessionId);
        loginLog.setTraceId(traceId);
        loginLog.setFailureReason(failureReason);
        loginLog.setCreatedBy("system");
        loginLog.setCreatedTime(OffsetDateTime.now());
        loginLog.setUpdatedBy("system");
        loginLog.setUpdatedTime(OffsetDateTime.now());
        loginLog.setDeleted(false);
        loginLog.setVersion(0);
        return loginLog;
    }
}
