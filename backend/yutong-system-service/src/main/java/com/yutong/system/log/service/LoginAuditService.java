package com.yutong.system.log.service;

import com.yutong.auth.AuthContext;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.domain.SysLoginLog;
import com.yutong.system.log.mapper.SysLoginLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;

/**
 * 登录审计服务。设计来源: 67-数据权限与审计日志详设 sys_login_log
 *
 * <p>负责记录登录/退出/Token 刷新/登录失败等会话事件，写入 {@code sys_login_log} 表。
 * 67 号文档验收标准 (line 165): "登录成功、登录失败、退出、Token 失效都有 sys_login_log"。
 *
 * <p>枚举对齐 67 号文档 (line 117-124):
 * <ul>
 *   <li>login_type: PASSWORD/OIDC/LDAP/CAS/MOCK/TOKEN_REFRESH/LOGOUT</li>
 *   <li>login_result: SUCCESS/FAILED/DENIED/EXPIRED</li>
 *   <li>device_type: WEB/MOBILE/API/UNKNOWN</li>
 * </ul>
 *
 * <p>异常容错: 审计写入失败不阻断登录流程，仅记录 ERROR 日志 (与 OperationLogService 一致策略)。
 * 防止审计组件故障导致用户无法登录。
 *
 * <p>GA2-L175 落地: 接入 AuthController login/logout/refresh-token 三端点，
 * login 端点同时处理成功 (SUCCESS) 和失败 (FAILED) 两条记录。
 */
@Service
public class LoginAuditService {

    private static final Logger log = LoggerFactory.getLogger(LoginAuditService.class);

    /** 审计写入失败兜底日志前缀。 */
    private static final String AUDIT_WRITE_FAILURE = "登录审计写入失败 loginType={} username={} - ";

    // ===== login_type 枚举 (67 号文档 line 117) =====
    public static final String TYPE_PASSWORD = "PASSWORD";
    public static final String TYPE_OIDC = "OIDC";
    public static final String TYPE_LDAP = "LDAP";
    public static final String TYPE_CAS = "CAS";
    public static final String TYPE_MOCK = "MOCK";
    public static final String TYPE_TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String TYPE_LOGOUT = "LOGOUT";

    // ===== login_result 枚举 (67 号文档 line 118) =====
    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_DENIED = "DENIED";
    public static final String RESULT_EXPIRED = "EXPIRED";

    // ===== device_type 枚举 (67 号文档 line 124) =====
    public static final String DEVICE_WEB = "WEB";
    public static final String DEVICE_MOBILE = "MOBILE";
    public static final String DEVICE_API = "API";
    public static final String DEVICE_UNKNOWN = "UNKNOWN";

    private final SysLoginLogMapper loginLogMapper;

    public LoginAuditService(SysLoginLogMapper loginLogMapper) {
        this.loginLogMapper = loginLogMapper;
    }

    /**
     * 记录登录事件 (成功或失败)。供 AuthController.login() 调用。
     *
     * <p>自动填充:
     * <ul>
     *   <li>id: ULID</li>
     *   <li>tenantId/userId/username: 优先从 {@link AuthContext} 取 (登录成功后已填充)，
     *       失败场景 AuthContext 可能为空，则从 {@link CurrentUserContext} 或入参 username 取</li>
     *   <li>traceId: {@link TraceContext}</li>
     *   <li>ip/userAgent/deviceType: 从当前 {@link HttpServletRequest} 解析</li>
     *   <li>loginTime: 当前时间</li>
     * </ul>
     *
     * @param loginType   登录类型 (PASSWORD/MOCK 等)
     * @param loginResult 结果 (SUCCESS/FAILED/DENIED)
     * @param failReason  失败原因摘要 (成功时为 null)，禁止写明真实密码
     * @param username    登录账号 (失败时 AuthContext 可能未生成，从入参取)
     * @param ctx         登录成功后的 AuthContext (失败时可为 null)
     * @param tokenId     Token 摘要或 jti (失败时可为 null)
     */
    public void recordLogin(String loginType, String loginResult, String failReason,
                            String username, AuthContext ctx, String tokenId) {
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setId(IdGenerator.nextId());
            // tenantId 优先从 ctx 取，失败场景回退 CurrentUserContext 或默认 "default"
            String tenantId = ctx != null ? ctx.tenantId() : CurrentUserContext.getTenantId();
            entity.setTenantId(tenantId != null ? tenantId : "default");
            entity.setLoginType(loginType);
            entity.setLoginResult(loginResult);
            entity.setFailReason(truncate(failReason, 256));
            entity.setUserId(ctx != null ? ctx.userId() : null);
            // username 优先 ctx，回退入参 (失败场景 ctx 可能为空)
            entity.setUsername(ctx != null ? ctx.username() : username);
            entity.setTokenId(truncate(tokenId, 128));
            entity.setTraceId(TraceContext.getTraceId());
            entity.setLoginTime(OffsetDateTime.now());

            // 从当前 HTTP 请求填充 ip/userAgent/deviceType
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                entity.setIp(truncate(resolveClientIp(request), 64));
                String ua = request.getHeader("User-Agent");
                entity.setUserAgent(truncate(ua, 512));
                entity.setDeviceType(resolveDeviceType(ua));
            } else {
                entity.setDeviceType(DEVICE_UNKNOWN);
            }

            loginLogMapper.insert(entity);
        } catch (Exception e) {
            log.error(AUDIT_WRITE_FAILURE + e.getMessage(), loginType, username, e);
        }
    }

    /**
     * 记录退出事件 (login_type=LOGOUT, login_result=SUCCESS)。供 AuthController.logout() 调用。
     * 同时回填 logoutTime。
     *
     * @param ctx    当前 AuthContext (退出前的用户)
     * @param tokenId Token 摘要 (可为 null)
     */
    public void recordLogout(AuthContext ctx, String tokenId) {
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(ctx != null ? ctx.tenantId() : CurrentUserContext.getTenantId());
            entity.setLoginType(TYPE_LOGOUT);
            entity.setLoginResult(RESULT_SUCCESS);
            entity.setUserId(ctx != null ? ctx.userId() : CurrentUserContext.getUserId());
            entity.setUsername(ctx != null ? ctx.username() : CurrentUserContext.getUsername());
            entity.setTokenId(truncate(tokenId, 128));
            entity.setTraceId(TraceContext.getTraceId());
            OffsetDateTime now = OffsetDateTime.now();
            entity.setLoginTime(now);
            entity.setLogoutTime(now);

            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                entity.setIp(truncate(resolveClientIp(request), 64));
                String ua = request.getHeader("User-Agent");
                entity.setUserAgent(truncate(ua, 512));
                entity.setDeviceType(resolveDeviceType(ua));
            } else {
                entity.setDeviceType(DEVICE_UNKNOWN);
            }

            loginLogMapper.insert(entity);
        } catch (Exception e) {
            log.error(AUDIT_WRITE_FAILURE + e.getMessage(), TYPE_LOGOUT,
                    ctx != null ? ctx.username() : null, e);
        }
    }

    /**
     * 记录 Token 刷新事件 (login_type=TOKEN_REFRESH)。供 AuthController.refreshToken() 调用。
     *
     * @param loginResult 刷新结果 (SUCCESS/FAILED/EXPIRED)
     * @param ctx         当前 AuthContext
     * @param tokenId     新 Token 摘要 (可为 null)
     * @param failReason  失败原因 (成功时为 null)
     */
    public void recordTokenRefresh(String loginResult, AuthContext ctx, String tokenId, String failReason) {
        try {
            SysLoginLog entity = new SysLoginLog();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(ctx != null ? ctx.tenantId() : CurrentUserContext.getTenantId());
            entity.setLoginType(TYPE_TOKEN_REFRESH);
            entity.setLoginResult(loginResult);
            entity.setFailReason(truncate(failReason, 256));
            entity.setUserId(ctx != null ? ctx.userId() : CurrentUserContext.getUserId());
            entity.setUsername(ctx != null ? ctx.username() : CurrentUserContext.getUsername());
            entity.setTokenId(truncate(tokenId, 128));
            entity.setTraceId(TraceContext.getTraceId());
            entity.setLoginTime(OffsetDateTime.now());

            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                entity.setIp(truncate(resolveClientIp(request), 64));
                String ua = request.getHeader("User-Agent");
                entity.setUserAgent(truncate(ua, 512));
                entity.setDeviceType(resolveDeviceType(ua));
            } else {
                entity.setDeviceType(DEVICE_UNKNOWN);
            }

            loginLogMapper.insert(entity);
        } catch (Exception e) {
            log.error(AUDIT_WRITE_FAILURE + e.getMessage(), TYPE_TOKEN_REFRESH,
                    ctx != null ? ctx.username() : null, e);
        }
    }

    /**
     * 解析客户端真实 IP。优先取 X-Forwarded-For / X-Real-IP (反向代理场景)，回退 remoteAddr。
     * 与 OperationLogService.resolveClientIp 实现保持一致。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * 根据 User-Agent 推断设备类型。
     * <ul>
     *   <li>含 Mobile/Android/iPhone/iPad → MOBILE</li>
     *   <li>无 User-Agent 或 curl/Postman 等 → API</li>
     *   <li>其他 (浏览器) → WEB</li>
     * </ul>
     */
    private String resolveDeviceType(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return DEVICE_API;
        }
        String ua = userAgent.toLowerCase();
        if (ua.contains("mobile") || ua.contains("android") || ua.contains("iphone") || ua.contains("ipad")) {
            return DEVICE_MOBILE;
        }
        if (ua.contains("curl") || ua.contains("postman") || ua.contains("okhttp") || ua.contains("java/")) {
            return DEVICE_API;
        }
        return DEVICE_WEB;
    }

    /** 字符串截断保护，防止超长字段写入数据库失败。 */
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }
}
