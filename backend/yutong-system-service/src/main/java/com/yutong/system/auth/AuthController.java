package com.yutong.system.auth;

import com.yutong.auth.AuthAdapter;
import com.yutong.auth.AuthContext;
import com.yutong.auth.PublicEndpoint;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.auth.online.OnlineUserService;
import com.yutong.system.auth.refresh.RefreshTokenPayload;
import com.yutong.system.auth.refresh.RefreshTokenStore;
import com.yutong.system.log.service.LoginAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 认证接口 (local/test Mock)。
 * 设计来源: 25-本地开发与工程初始化手册 (POST /api/v1/auth/login 返回 token)
 * 生产环境必须由真实 AuthAdapter 提供登录能力。
 *
 * <p>GA2-L175: 接入登录审计 (67 号文档 line 165 验收标准)。
 * <ul>
 *   <li>login 端点: 成功 → recordLogin(MOCK, SUCCESS)；失败 → recordLogin(MOCK, FAILED, 错误信息)</li>
 *   <li>logout 端点: recordLogout(ctx, token)</li>
 *   <li>refresh-token 端点: recordTokenRefresh(SUCCESS, ctx, token)</li>
 * </ul>
 * 审计写入失败不阻断业务流程 (LoginAuditService 内部 try-catch 容错)。
 */
@Tag(name = "认证")
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthAdapter authAdapter;
    private final LoginAuditService loginAuditService;
    private final RefreshTokenStore refreshTokenStore;
    private final OnlineUserService onlineUserService;

    public AuthController(AuthAdapter authAdapter, LoginAuditService loginAuditService,
                          RefreshTokenStore refreshTokenStore, OnlineUserService onlineUserService) {
        this.authAdapter = authAdapter;
        this.loginAuditService = loginAuditService;
        this.refreshTokenStore = refreshTokenStore;
        this.onlineUserService = onlineUserService;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /** 21-安全合规: refresh token 轮换请求体。 */
    public record RefreshRequest(String refreshToken) {}

    @PublicEndpoint
    @Operation(summary = "Mock 登录", operationId = "login")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        try {
            String token = authAdapter.login(request.username(), request.password());
            AuthContext ctx = authAdapter.current();
            // P1-D: 注册在线会话 (在线用户监控)
            onlineUserService.register(token, ctx.userId(), ctx.username(),
                    ctx.tenantId(), resolveIp(httpRequest), httpRequest.getHeader("User-Agent"));
            // GA2-L175: 登录成功审计 (login_type=MOCK, login_result=SUCCESS)
            loginAuditService.recordLogin(
                    LoginAuditService.TYPE_MOCK,
                    LoginAuditService.RESULT_SUCCESS,
                    null,
                    request.username(),
                    ctx,
                    tokenSummary(token));
            // GA2-L178: 返回 mockUserType + roles，供前端后续请求携带 X-Mock-User 头（68 号文档 Demo 账号体系）
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userId", ctx.userId());
            data.put("username", ctx.username());
            data.put("tenantId", ctx.tenantId());
            data.put("mock", ctx.mock());
            data.put("roles", ctx.roles());
            data.put("permissions", ctx.permissions());
            data.put("dataScopeType", ctx.dataScopeType());
            // Demo 账号登录后返回 mockUserType，前端后续请求携带 X-Mock-User: <mockUserType> 头
            // 非 Demo 账号（如直接用 admin/biz）mockUserType 为 null，前端按原逻辑处理
            String mockUserType = null;
            if (ctx.roles() != null && !ctx.roles().isEmpty()) {
                mockUserType = resolveMockUserTypeFromRoles(ctx);
                data.put("mockUserType", mockUserType);
            }
            // 21-安全合规: 签发 refresh token (14d, 轮换)，与 access token (15min) 配合
            // refresh token 存储 Redis，每次使用必须轮换 (设计文档 21 号要求)
            RefreshTokenPayload rtPayload = new RefreshTokenPayload(
                    ctx.userId(), ctx.username(), ctx.tenantId(), mockUserType, token);
            String refreshToken = refreshTokenStore.issue(rtPayload);
            data.put("refreshToken", refreshToken);
            return Result.ok(data, TraceContext.getTraceId());
        } catch (RuntimeException e) {
            // GA2-L175: 登录失败审计 (login_type=MOCK, login_result=FAILED)
            // fail_reason 仅记录错误摘要，禁止写明真实密码
            loginAuditService.recordLogin(
                    LoginAuditService.TYPE_MOCK,
                    LoginAuditService.RESULT_FAILED,
                    safeReason(e),
                    request.username(),
                    null,
                    null);
            throw e;
        }
    }

    @Operation(summary = "当前用户信息", operationId = "getCurrentUser")
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        AuthContext ctx = authAdapter.current();
        return Result.ok(Map.of(
                "userId", ctx.userId(),
                "username", ctx.username(),
                "tenantId", ctx.tenantId(),
                "roles", ctx.roles(),
                "permissions", ctx.permissions(),
                "dataScopeType", ctx.dataScopeType()
        ), TraceContext.getTraceId());
    }

    /**
     * 设计契约别名端点 (GET /auth/user-info 对齐)。
     * 委托给 {@link #me()}, 保持前端 /auth/me 路径不变的同时兼容设计契约。
     */
    @Operation(summary = "当前用户信息(契约别名)", operationId = "getCurrentUserInfo")
    @GetMapping("/user-info")
    public Result<Map<String, Object>> userInfo() {
        return me();
    }

    @Operation(summary = "注销", operationId = "logout")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest httpRequest) {
        AuthContext ctx = authAdapter.current();
        // P1-D: 移除在线会话
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader != null && !authHeader.isBlank()) {
            onlineUserService.remove(authHeader);
        }
        // GA2-L175: 退出审计 (login_type=LOGOUT, login_result=SUCCESS)
        // 注：token 摘要在 Mock 模式下无法从 AuthContext 获取，传 null
        loginAuditService.recordLogout(ctx, null);
        authAdapter.logout();
        return Result.ok(null, TraceContext.getTraceId());
    }

    @Operation(summary = "刷新 Token", operationId = "refreshToken")
    @PostMapping("/refresh-token")
    public Result<Map<String, Object>> refreshToken() {
        AuthContext ctx = authAdapter.current();
        // GA2-L175: Token 刷新审计 (login_type=TOKEN_REFRESH, login_result=SUCCESS)
        loginAuditService.recordTokenRefresh(
                LoginAuditService.RESULT_SUCCESS,
                ctx,
                null,
                null);
        return Result.ok(Map.of(
                "userId", ctx.userId(),
                "username", ctx.username(),
                "tenantId", ctx.tenantId(),
                "roles", ctx.roles(),
                "permissions", ctx.permissions()
        ), TraceContext.getTraceId());
    }

    /**
     * 21-安全合规: refresh token 轮换端点。
     *
     * <p>设计来源: 21-安全合规详设「access=15min + refresh=14d，refresh token 每次使用必须轮换」。
     *
     * <p>请求体 {@code {refreshToken}}，校验通过后:
     * <ol>
     *   <li>删除旧 refresh token (Redis key 删除，实现轮换)</li>
     *   <li>签发新 refresh token (存入 Redis，TTL=14d)</li>
     *   <li>重签 access token 并返回</li>
     * </ol>
     *
     * <p>refresh token 不存在/已过期/已被轮换 → 返回 AUTH-401002 (HTTP 401)，前端跳转登录。
     * 此端点为 {@link PublicEndpoint}，无需有效 access token 即可调用 (access token 可能已过期)。
     *
     * <p>注意: 此端点与 {@link #refreshToken()} (/refresh-token) 不同：
     * /refresh-token 依赖当前请求的 access token 上下文 (旧契约，仅返回用户信息)；
     * /refresh 依赖请求体中的 refresh token (新契约，返回新 token pair)。
     */
    @PublicEndpoint
    @Operation(summary = "刷新 Token(契约别名)", operationId = "refresh")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody(required = false) RefreshRequest request) {
        String refreshTokenValue = request != null ? request.refreshToken() : null;
        RefreshTokenStore.RotationResult result = refreshTokenStore.validateAndRotate(refreshTokenValue);
        if (result == null) {
            // refresh token 无效/已过期，记录审计并返回 401
            loginAuditService.recordTokenRefresh(
                    LoginAuditService.RESULT_EXPIRED, null, null,
                    "refresh token invalid or expired");
            throw new BusinessException(ErrorCode.AUTH_TOKEN_EXPIRED,
                    "refresh token 无效或已过期，请重新登录");
        }
        RefreshTokenPayload payload = result.payload();
        String newAccessToken = regenerateAccessToken(payload);
        String newRefreshToken = result.newRefreshToken();
        // GA2-L175: Token 刷新审计 (login_type=TOKEN_REFRESH, login_result=SUCCESS)
        loginAuditService.recordTokenRefresh(
                LoginAuditService.RESULT_SUCCESS, null,
                tokenSummary(newAccessToken), null);
        Map<String, Object> data = new HashMap<>();
        data.put("token", newAccessToken);
        data.put("refreshToken", newRefreshToken);
        return Result.ok(data, TraceContext.getTraceId());
    }

    /**
     * 重签 access token。
     *
     * <p>Mock 模式: 重新生成 mock-token (与 {@link com.yutong.auth.MockAuthAdapter#login} 一致)，
     * token 本体不被校验，用户身份由 X-Mock-User 头或请求属性决定。
     * 生产模式: 应调用 {@code StpUtil.login(payload.userId())} 获取新 Sa-Token (待真实 AuthAdapter 接入)。
     */
    private String regenerateAccessToken(RefreshTokenPayload payload) {
        String userType = payload.mockUserType() != null ? payload.mockUserType() : "default";
        return "mock-token-" + System.currentTimeMillis() + "-" + userType;
    }

    @Operation(summary = "当前用户菜单树", operationId = "getCurrentUserMenus")
    @GetMapping("/menus")
    public Result<List<Map<String, Object>>> getCurrentUserMenus() {
        AuthContext ctx = authAdapter.current();
        return Result.ok(buildUserMenuTree(ctx), TraceContext.getTraceId());
    }

    /** 菜单定义项。permission 为 null/空 表示父节点（按子菜单可见性决定是否展示）。 */
    private record MenuDef(String id, String parentId, String title, String path,
                            String icon, int sort, String permission) {}

    /**
     * 菜单目录定义。基于前端路由 web-admin/src/router/index.ts 推导核心菜单。
     * 父节点 (permission 为空) 仅当存在可见子菜单时才返回。
     */
    private List<MenuDef> menuCatalog() {
        return List.of(
                new MenuDef("m_dashboard", "0", "工作台", "/dashboard", "Dashboard", 1, "dashboard:view"),
                new MenuDef("m_customers", "0", "客户管理", "/customers", "Team", 2, "biz:customer:list"),
                new MenuDef("m_products", "0", "商品管理", "/products", "Shop", 3, "biz:product:list"),
                new MenuDef("m_requests", "0", "申请单管理", "/requests", "FileText", 4, "biz:request:list"),
                new MenuDef("m_contracts", "0", "合同档案", "/contracts", "Folder", 5, "system:todo:list"),
                new MenuDef("m_reports", "0", "报表分析", "/reports", "BarChart", 6, "report:view"),
                new MenuDef("m_ai", "0", "AI 助手", "/ai-chat", "Robot", 7, "ai:assistant:use"),
                new MenuDef("m_workflow", "0", "工作流", "/workflow", "Apartment", 8, null),
                new MenuDef("m_workflow_def", "m_workflow", "流程定义", "/workflow/definitions", "Branches", 1, "system:todo:list"),
                new MenuDef("m_workflow_inst", "m_workflow", "流程实例", "/workflow/instances", "ShareAlt", 2, "system:todo:list"),
                new MenuDef("m_workflow_todo", "m_workflow", "任务待办", "/workflow/todo", "CheckCircle", 3, "system:todo:list"),
                new MenuDef("m_lowcode", "0", "低代码", "/lowcode", "Block", 9, null),
                new MenuDef("m_lc_entities", "m_lowcode", "低代码实体", "/lc-entities", "Database", 1, "lc:entity:list"),
                new MenuDef("m_lc_pages", "m_lowcode", "低代码页面", "/lc-pages", "Layout", 2, "lc:page:list"),
                new MenuDef("m_plugin", "0", "插件", "/plugin", "Appstore", 10, null),
                new MenuDef("m_plugins", "m_plugin", "插件管理", "/plugins", "Tool", 1, "plugin:view"),
                new MenuDef("m_templates", "m_plugin", "模板市场", "/templates", "Copy", 2, "template:view"),
                new MenuDef("m_system", "0", "系统管理", "/system", "Setting", 11, null),
                new MenuDef("m_sys_config", "m_system", "参数配置", "/system/configs", "Control", 1, "system:config:list"),
                new MenuDef("m_sys_oplog", "m_system", "操作日志", "/system/operation-logs", "Audit", 2, "system:operation-log:list"),
                new MenuDef("m_sys_dict", "m_system", "字典类型", "/dict-types", "Book", 3, "system:dict:list"),
                new MenuDef("m_sys_license", "m_system", "商业授权", "/system/license", "SafetyCertificate", 4, "system:config:list"),
                new MenuDef("m_monitor", "m_system", "服务健康", "/monitor/health", "Heart", 5, "monitor:health:view")
        );
    }

    /**
     * 根据当前用户权限构建菜单树。admin 通配权限 "*" 可见全部菜单。
     * 无登录上下文或无权限返回空列表（fail-closed，不泄露菜单结构）。
     */
    private List<Map<String, Object>> buildUserMenuTree(AuthContext ctx) {
        Set<String> permissions = (ctx != null && ctx.permissions() != null) ? ctx.permissions() : Set.of();
        boolean isSuperAdmin = permissions.contains("*");
        List<MenuDef> visible = new ArrayList<>();
        for (MenuDef m : menuCatalog()) {
            if (isSuperAdmin || m.permission() == null || m.permission().isBlank()
                    || permissions.contains(m.permission())) {
                visible.add(m);
            }
        }
        return buildMenuTree(visible, "0");
    }

    /** 递归构建菜单树。父节点若无可见子菜单则被剔除。 */
    private List<Map<String, Object>> buildMenuTree(List<MenuDef> menus, String parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (MenuDef m : menus) {
            if (!parentId.equals(m.parentId())) {
                continue;
            }
            List<Map<String, Object>> children = buildMenuTree(menus, m.id());
            boolean isParent = m.permission() == null || m.permission().isBlank();
            if (isParent && children.isEmpty()) {
                continue;
            }
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", m.id());
            node.put("parentId", m.parentId());
            node.put("title", m.title());
            node.put("path", m.path());
            node.put("icon", m.icon());
            node.put("sort", m.sort());
            node.put("children", children);
            tree.add(node);
        }
        tree.sort((a, b) -> Integer.compare((Integer) a.get("sort"), (Integer) b.get("sort")));
        return tree;
    }

    @Operation(summary = "当前用户权限", operationId = "getCurrentUserPermissions")
    @GetMapping("/permissions")
    public Result<Map<String, Object>> getCurrentUserPermissions() {
        AuthContext ctx = authAdapter.current();
        return Result.ok(Map.of(
                "roles", ctx.roles(),
                "permissions", ctx.permissions(),
                "dataScopeType", ctx.dataScopeType()
        ), TraceContext.getTraceId());
    }

    /** 生成 token 摘要：保留前 32 字符 + 长度，避免完整 token 进入审计日志。 */
    private String tokenSummary(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return token.length() <= 32 ? token : token.substring(0, 32) + "...(" + token.length() + ")";
    }

    /** 安全提取异常消息作为 fail_reason，避免敏感信息泄漏。 */
    private String safeReason(RuntimeException e) {
        String msg = e.getMessage();
        if (msg == null) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 256 ? msg.substring(0, 256) : msg;
    }

    /**
     * GA2-L178: 根据角色推断 Mock 用户类型，供前端后续请求携带 X-Mock-User 头。
     * 角色 → Mock 用户类型映射: ADMIN→admin / BIZ_USER→biz / APPROVER→approver / VIEWER→viewer。
     */
    private String resolveMockUserTypeFromRoles(AuthContext ctx) {
        if (ctx.roles() == null || ctx.roles().isEmpty()) {
            return null;
        }
        for (String role : ctx.roles()) {
            switch (role) {
                case "ADMIN": return "admin";
                case "BIZ_USER": return "biz";
                case "APPROVER": return "approver";
                case "VIEWER": return "viewer";
            }
        }
        return null;
    }

    /** 解析客户端 IP (支持 X-Forwarded-For / X-Real-IP / remoteAddr)。 */
    private String resolveIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }
}
