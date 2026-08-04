package com.yutong.auth;

import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.exception.PermissionDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Set;

/**
 * Mock 鉴权适配器。仅 local/test profile 启用。
 * 设计来源: 15-权限接入边界设计 第 128 行、67-数据权限与审计日志详设 第 75-79 行、68-演示环境与样例数据剧本详设
 * 生产 profile 启用 Mock 将导致启动失败 (见 MockAuthGuardConfig 校验)。
 *
 * GA2-02 扩展: 支持 4 类 Mock 用户切换，通过 HTTP 请求头 X-Mock-User 控制:
 *  - admin    → ALL 数据范围 + 全部权限 + 可见敏感字段（默认）
 *  - biz      → SELF 数据范围（仅本人创建数据）+ 业务权限 + 可见敏感字段
 *  - approver → CUSTOM 数据范围（白名单 resourceIds）+ 审批权限 + 可见敏感字段
 *  - viewer   → TENANT 数据范围（租户内全部）+ 只读权限 + 不可见敏感字段（脱敏）
 *
 * GA2-L178 扩展: 支持 68 号文档 Demo 账号 username/password 登录:
 *  - admin_demo     → 映射 admin 用户类型（管理员，全部演示能力）
 *  - reviewer_demo  → 映射 approver 用户类型（审核人，待办审核）
 *  - user_demo      → 映射 biz 用户类型（业务人员，创建申请单）
 *  - viewer_demo    → 映射 viewer 用户类型（只读用户，查看报表/文档）
 *  密码统一由环境变量 YUTONG_DEMO_PASSWORD 初始化（默认 demo123），不写入文档明文。
 *  登录成功后通过 ThreadLocal 暂存用户类型，后续 current()/getDataScope() 优先读取 ThreadLocal。
 *
 * 测试用例对齐 67 号文档第 75-79 行:
 *  - admin    ALL    → 全部申请单可见
 *  - biz     SELF    → 仅本人 applicant_id/owner_user_id 的申请单
 *  - approver CUSTOM → 仅白名单 resourceIds 中的申请单（空集 → 空结果）
 *  - viewer  TENANT  → 租户内全部申请单（敏感字段脱敏）
 */
@Primary
@Component
public class MockAuthAdapter implements AuthAdapter {

    /** 切换 Mock 用户的请求头名。 */
    public static final String MOCK_USER_HEADER = "X-Mock-User";

    /** 4 类 Mock 用户标识。 */
    public static final String MOCK_ADMIN = "admin";
    public static final String MOCK_BIZ = "biz";
    public static final String MOCK_APPROVER = "approver";
    public static final String MOCK_VIEWER = "viewer";

    /** GA2-L178: 68 号文档 Demo 账号 username → Mock 用户类型映射。 */
    public static final String DEMO_ADMIN_USERNAME = "admin_demo";
    public static final String DEMO_REVIEWER_USERNAME = "reviewer_demo";
    public static final String DEMO_USER_USERNAME = "user_demo";
    public static final String DEMO_VIEWER_USERNAME = "viewer_demo";

    /** Demo 账号密码环境变量名（68 号文档: 密码统一由环境变量初始化）。 */
    public static final String DEMO_PASSWORD_ENV = "YUTONG_DEMO_PASSWORD";
    private static final String DEMO_PASSWORD_DEFAULT = "demo123";

    /** 默认租户。 */
    private static final String DEFAULT_TENANT = "default";

    /**
     * GA2-L178: 登录时暂存 Mock 用户类型的请求属性 key。
     * login() 通过 RequestContextHolder 写入请求属性，current() 优先读取。
     * 请求结束时 RequestContextHolder 自动清理，避免线程池复用串号。
     */
    private static final String LOGIN_USER_TYPE_ATTR = "MockAuthAdapter.loginUserType";

    /**
     * 登录时暂存原始 username，供同请求内构建 AuthContext 时回显（如 admin_demo 登录后 username=admin_demo）。
     */
    private static final String LOGIN_USERNAME_ATTR = "MockAuthAdapter.loginUsername";

    /** admin 用户: 全部权限 + ALL 数据范围。 */
    private static final Set<String> ADMIN_PERMISSIONS = Set.of("*");
    private static final Set<String> ADMIN_ROLES = Set.of("ADMIN");

    /** biz 用户: 业务权限 + SELF 数据范围。
     *  GA2-03-5: 授予 AI 工具权限 (TC-SEC-AI-001)，biz 可使用 AI 生成页面/SQL 草稿与平台问答。
     *  GA2-03-6: 权限码对齐 @RequiresPermission 注解命名 (add/edit 而非 create/update，system: 而非 sys:)。
     *  GA2-16: 增加 *:list 菜单可见性权限码 (设计 96 号文档菜单权限矩阵)。
     *  GA2-L190: 补全 biz 用户移动端入口权限码 (mobile:* + ai:conversation:list) 对齐
     *  MobileController/AiChatController @RequiresPermission 注解, 修复 CT 契约测试 403 失败。 */
    private static final Set<String> BIZ_PERMISSIONS = Set.of(
            "biz:request:add", "biz:request:edit", "biz:request:submit", "biz:request:withdraw",
            // GA2-L189: 补全 biz 用户申请单详情+删除权限 (biz:request:detail/delete) 对齐 CT 契约测试
            "biz:request:detail", "biz:request:delete",
            // GA2-L180: 权限码对齐契约源 permissions.yaml/openapi.yaml (masterdata:customer:* → biz:customer:*)
            "biz:customer:add", "biz:customer:edit",
            // GA2-L181: 补全 biz 用户 customer CRUD 权限 (detail/delete/phone:view) 对齐 CT 契约测试
            "biz:customer:detail", "biz:customer:delete", "biz:customer:phone:view",
            // GA2-L186: 权限码对齐契约源 permissions.yaml/openapi.yaml (masterdata:product:* → biz:product:*)
            "biz:product:add", "biz:product:edit", "biz:product:detail", "biz:product:delete",
            "biz:customer:import", "biz:customer:export",
            "biz:product:import", "biz:product:export",
            "system:file:upload", "system:file:detail",
            // GA2-L188: 补全 File 域权限码对齐 openapi.yaml x-permission
            // (list/download/preview/delete + 保留 detail 兼容)
            "system:file:list", "system:file:preview", "system:file:download", "system:file:delete",
            "system:todo:complete", "system:message:read",
            "ai:tool:generate", "ai:tool:sql", "ai:tool:meta",
            // GA2-16: 菜单可见性权限码 (96 号文档菜单矩阵 biz 列)
            "dashboard:view",
            "biz:request:list", "biz:customer:list", "biz:product:list",
            "system:message:list", "system:todo:list",
            "system:import-export-task:list",
            "ai:assistant:use",
            // GA2-L190: 移动端入口权限码 (MobileController @RequiresPermission), biz 用户为移动端主要使用者
            "mobile:workbench:view", "mobile:todo:list",
            "mobile:biz-request:detail", "mobile:scan:use",
            // GA2-L190: AI 会话列表查询权限 (AiChatController.pageConversations @RequiresPermission)
            "ai:conversation:list"
    );
    private static final Set<String> BIZ_ROLES = Set.of("BIZ_USER");

    /** approver 用户: 审批权限 + CUSTOM 数据范围（白名单申请单 ID）。
     *  GA2-03-5: 授予只读 AI 工具权限 (TC-SEC-AI-001)，approver 可使用 AI 平台问答。
     *  GA2-03-6: 权限码对齐 @RequiresPermission 注解命名。
     *  GA2-16: 增加 *:list 菜单可见性权限码 (设计 96 号文档菜单矩阵 approver 列)。
     *  GA2-L190: 补全移动端审批权限码 (mobile:biz-request:approve/reject), 使 @RequiresPermission
     *  通过后由服务层 doTransition DataScope 校验拒绝 (AUTH-403002), 对齐 CT 契约测试 CT-7。 */
    private static final Set<String> APPROVER_PERMISSIONS = Set.of(
            "biz:request:approve", "biz:request:reject", "biz:request:archive",
            "system:todo:complete", "system:message:read",
            "ai:tool:meta",
            // GA2-16: 菜单可见性权限码 (96 号文档菜单矩阵 approver 列)
            "dashboard:view",
            "biz:request:list",
            "system:message:list", "system:todo:list",
            "ai:assistant:use",
            // GA2-L190: 移动端审批权限码 (MobileController @RequiresPermission)。
            // 授予后 @RequiresPermission 通过, 服务层 doTransition DataScope 校验拒绝 → AUTH-403002
            // (approver CUSTOM 空白名单 → 数据范围拒绝), 对齐 CT 契约测试 CT-7 用例。
            "mobile:biz-request:approve", "mobile:biz-request:reject"
    );
    private static final Set<String> APPROVER_ROLES = Set.of("APPROVER");

    /** viewer 用户: 只读权限 + TENANT 数据范围 + 不可见敏感字段。
     *  GA2-03-5: 故意不授予 ai:tool:* 权限，作为 TC-SEC-AI-001 拒绝用例 (AUTH-403001)。
     *  GA2-03-6: 权限码对齐 @RequiresPermission 注解命名；viewer 无写权限，仅可读消息。
     *  GA2-16: 增加 *:list 菜单可见性权限码 (设计 96 号文档菜单矩阵 viewer 列 "只读可选")。
     *  GA2-36: 增加 report:view / report:dataset:view 只读报表权限，验证 TENANT 范围 + 列级脱敏。
     *  GA2-L190: 增加 ai:conversation:list 只读权限, 对齐 AiChatController @RequiresPermission,
     *  使 CT-listAiConversations CT-3 数据隔离用例 (viewer 查询看不到 biz 的会话) 可执行。 */
    private static final Set<String> VIEWER_PERMISSIONS = Set.of(
            "system:message:read",
            // GA2-16: 菜单可见性权限码 (96 号文档菜单矩阵 viewer 列)
            "dashboard:view",
            "biz:request:list", "biz:customer:list", "biz:product:list",
            // GA2-L189: viewer 可查申请单详情 (biz:request:detail), 对齐 CT-getBizRequest 契约测试
            "biz:request:detail",
            // GA2-L181: viewer 可查客户详情 (biz:customer:detail), 但无 phone:view → contactPhone 脱敏
            "biz:customer:detail",
            // GA2-L186: viewer 可查商品详情 (biz:product:detail), 对齐 CT-getProduct 契约测试
            "biz:product:detail",
            "system:message:list",
            "system:import-export-task:list",
            // GA2-L188: viewer 文件只读权限 (list/preview/download), 无 upload/delete
            "system:file:list", "system:file:preview", "system:file:download",
            // GA2-36: 报表只读权限，验证 viewer 数据范围(TENANT) + 列级脱敏(viewer 不可见敏感字段)
            "report:view", "report:dataset:view",
            // GA2-L190: AI 会话列表只读权限 (按 userId 隔离, viewer 仅看到自己的会话)
            "ai:conversation:list"
    );
    private static final Set<String> VIEWER_ROLES = Set.of("VIEWER");

    /** approver 用户的 CUSTOM 白名单（申请单 ID 集合）。空集 → 空结果（严禁降级）。 */
    private static final Set<String> APPROVER_RESOURCE_IDS = Set.of(
            // 与 R__seed_demo_data.sql 中 approver 可见的申请单 ID 对齐
            // 默认空集：测试 "白名单为空 → 返回空结果" 用例
            // 真实场景由 sys_role_data_scope 表存储，GA2-02 用内存常量
    );

    @Override
    public AuthContext current() {
        String userType = resolveMockUserType();
        return buildContext(userType);
    }

    /**
     * GA2-L178: Demo 账号 username/password 登录。
     * 设计来源: 68-演示环境与样例数据剧本详设 line 30-37。
     * 4 类 Demo 账号 username → Mock 用户类型映射:
     *  - admin_demo     → admin
     *  - reviewer_demo  → approver
     *  - user_demo      → biz
     *  - viewer_demo    → viewer
     * 密码统一校验环境变量 YUTONG_DEMO_PASSWORD（默认 demo123）。
     * 兼容旧模式: 非 Demo username（如 admin/biz/approver/viewer）直接放行，由 X-Mock-User 头控制用户类型。
     * 登录成功后通过 RequestContextHolder 请求属性暂存用户类型，供当前请求后续 current()/getDataScope() 使用。
     * 请求结束时 RequestContextHolder 自动清理，无需手动 remove，避免线程池复用串号。
     */
    @Override
    public String login(String username, String password) {
        String userType = resolveUserTypeByUsername(username);
        if (userType != null) {
            // Demo 账号: 校验密码
            String expectedPassword = System.getenv(DEMO_PASSWORD_ENV);
            if (expectedPassword == null || expectedPassword.isBlank()) {
                expectedPassword = DEMO_PASSWORD_DEFAULT;
            }
            if (!expectedPassword.equals(password)) {
                throw new PermissionDeniedException("Demo 账号密码错误");
            }
            // 通过 RequestContextHolder 请求属性暂存用户类型（请求结束自动清理）
            try {
                RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    attrs.setAttribute(LOGIN_USER_TYPE_ATTR, userType, RequestAttributes.SCOPE_REQUEST);
                    attrs.setAttribute(LOGIN_USERNAME_ATTR, username.trim(), RequestAttributes.SCOPE_REQUEST);
                }
            } catch (Exception ignored) {
                // 非请求上下文（理论上 login 一定在请求中）忽略
            }
        } else {
            // 非 Demo username（如 admin/biz/approver/viewer 或任意值）: 兼容旧模式，由 X-Mock-User 头控制
            // 同样回显原始 username，保持登录响应 username 与请求一致
            try {
                RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
                if (attrs != null && username != null) {
                    attrs.setAttribute(LOGIN_USERNAME_ATTR, username.trim(), RequestAttributes.SCOPE_REQUEST);
                }
            } catch (Exception ignored) {
            }
        }
        return "mock-token-" + System.currentTimeMillis() + "-" + (userType != null ? userType : "default");
    }

    /** 根据 username 解析对应的 Mock 用户类型；非 Demo 账号返回 null（由 X-Mock-User 头控制）。 */
    private String resolveUserTypeByUsername(String username) {
        if (username == null) {
            return null;
        }
        return switch (username.trim().toLowerCase()) {
            case DEMO_ADMIN_USERNAME -> MOCK_ADMIN;
            case DEMO_REVIEWER_USERNAME -> MOCK_APPROVER;
            case DEMO_USER_USERNAME -> MOCK_BIZ;
            case DEMO_VIEWER_USERNAME -> MOCK_VIEWER;
            default -> null;
        };
    }

    @Override
    public void logout() {
        // no-op for mock（请求属性由 RequestContextHolder 自动清理）
    }

    @Override
    public void requirePermission(String code) {
        // Mock 模式按当前用户权限码校验；admin 全部放行
        AuthContext ctx = current();
        if (ctx == null) {
            throw new PermissionDeniedException("未登录");
        }
        if (ctx.hasPermission("*")) {
            return;
        }
        if (!ctx.hasPermission(code)) {
            throw new PermissionDeniedException("Mock 用户[" + ctx.username() + "]缺少权限: " + code);
        }
    }

    @Override
    public DataScope getDataScope(String userId, String tenantId, String resourceCode) {
        String userType = resolveMockUserType();
        String effectiveUserId = resolveUserId(userType);
        String effectiveTenant = tenantId != null ? tenantId : DEFAULT_TENANT;

        switch (userType) {
            case MOCK_ADMIN:
                return DataScope.all(effectiveUserId, effectiveTenant, resourceCode);
            case MOCK_BIZ:
                return DataScope.self(effectiveUserId, effectiveTenant, resourceCode);
            case MOCK_APPROVER:
                // CUSTOM: 白名单 resourceIds；空集 → 空结果（67 号文档第 52 行硬约束）
                return DataScope.custom(effectiveUserId, effectiveTenant, resourceCode,
                        APPROVER_RESOURCE_IDS, true);
            case MOCK_VIEWER:
                // TENANT 范围 + 不可见敏感字段（脱敏）
                return new DataScope(DataScopeType.TENANT, effectiveUserId, effectiveTenant, resourceCode,
                        Set.of(), Set.of(), Set.of(), Set.of(), false);
            default:
                return DataScope.none(effectiveUserId, effectiveTenant, resourceCode);
        }
    }

    /**
     * 从当前 HTTP 请求属性或请求头解析 Mock 用户类型。
     * 优先级: 请求属性 (login 设置) > X-Mock-User 请求头 > 默认 admin。
     * GA2-L178: Demo 账号 login() 后通过 RequestContextHolder 请求属性暂存用户类型，供同请求内 current() 使用。
     * 后续请求由前端携带 X-Mock-User 头指定用户类型（Mock 模式 token 仅占位）。
     */
    private String resolveMockUserType() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                // 1. 优先读取 login() 设置的请求属性（同请求内有效）
                Object loginType = attrs.getAttribute(LOGIN_USER_TYPE_ATTR, RequestAttributes.SCOPE_REQUEST);
                if (loginType instanceof String s && !s.isBlank()) {
                    return s;
                }
                // 2. 读取 X-Mock-User 请求头（后续请求由前端携带）
                if (attrs instanceof ServletRequestAttributes sra) {
                    HttpServletRequest request = sra.getRequest();
                    String header = request.getHeader(MOCK_USER_HEADER);
                    if (header != null && !header.isBlank()) {
                        String value = header.trim().toLowerCase();
                        if (MOCK_ADMIN.equals(value) || MOCK_BIZ.equals(value)
                                || MOCK_APPROVER.equals(value) || MOCK_VIEWER.equals(value)) {
                            return value;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // 非请求上下文（如异步任务、定时任务）使用默认 admin
        }
        // 3. 默认 admin
        return MOCK_ADMIN;
    }

    /** 根据用户类型构造对应的 AuthContext；登录请求内优先使用原始 username 回显。 */
    private AuthContext buildContext(String userType) {
        String loginUsername = resolveLoginUsername();
        return switch (userType) {
            case MOCK_ADMIN -> new AuthContext(
                    "01MOCKUSER0000000000000ADMIN",
                    loginUsername != null ? loginUsername : "admin", DEFAULT_TENANT,
                    ADMIN_PERMISSIONS, ADMIN_ROLES,
                    DataScopeType.ALL.name(),
                    "01MOCKDEPT0000000000000ROOT", "/corp",
                    true);
            case MOCK_BIZ -> new AuthContext(
                    "01MOCKUSER00000000000000BIZ",
                    loginUsername != null ? loginUsername : "biz_user", DEFAULT_TENANT,
                    BIZ_PERMISSIONS, BIZ_ROLES,
                    DataScopeType.SELF.name(),
                    "01MOCKDEPT000000000000SALES", "/corp/sales",
                    true);
            case MOCK_APPROVER -> new AuthContext(
                    "01MOCKUSER0000000000APPROVER",
                    loginUsername != null ? loginUsername : "approver", DEFAULT_TENANT,
                    APPROVER_PERMISSIONS, APPROVER_ROLES,
                    DataScopeType.CUSTOM.name(),
                    "01MOCKDEPT0000000000APPROVE", "/corp/approve",
                    true);
            case MOCK_VIEWER -> new AuthContext(
                    "01MOCKUSER000000000000VIEWER",
                    loginUsername != null ? loginUsername : "viewer", DEFAULT_TENANT,
                    VIEWER_PERMISSIONS, VIEWER_ROLES,
                    DataScopeType.TENANT.name(),
                    "01MOCKDEPT0000000000000ROOT", "/corp",
                    true);
            default -> new AuthContext(
                    "01MOCKUSER0000000000000ADMIN",
                    loginUsername != null ? loginUsername : "admin", DEFAULT_TENANT,
                    ADMIN_PERMISSIONS, ADMIN_ROLES,
                    DataScopeType.ALL.name(),
                    "01MOCKDEPT0000000000000ROOT", "/corp",
                    true);
        };
    }

    /** 读取 login() 暂存的原始 username；非登录请求或无属性时返回 null。 */
    private String resolveLoginUsername() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                Object value = attrs.getAttribute(LOGIN_USERNAME_ATTR, RequestAttributes.SCOPE_REQUEST);
                if (value instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** 根据用户类型返回对应的 userId。 */
    private String resolveUserId(String userType) {
        return switch (userType) {
            case MOCK_ADMIN -> "01MOCKUSER0000000000000ADMIN";
            case MOCK_BIZ -> "01MOCKUSER00000000000000BIZ";
            case MOCK_APPROVER -> "01MOCKUSER0000000000APPROVER";
            case MOCK_VIEWER -> "01MOCKUSER000000000000VIEWER";
            default -> "01MOCKUSER0000000000000ADMIN";
        };
    }
}
