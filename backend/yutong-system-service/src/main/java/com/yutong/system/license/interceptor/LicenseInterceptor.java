package com.yutong.system.license.interceptor;

import com.yutong.api.facade.LicenseService;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.license.service.LocalLicenseService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商业授权模块 API 拦截器。设计来源: 70-商业授权与版本能力裁剪详设「模块授权行为矩阵」、
 * contracts/governance/commercial-license-trimming.yaml#moduleAuthBehaviorMatrix (line 265)。
 *
 * <p>对 6 个商业模块的 API 路径进行强制授权校验:
 * <ul>
 *   <li>lowcode → /api/v1/lowcode/**</li>
 *   <li>ai → /api/v1/ai/** 和 /api/v1/ai-governance/**</li>
 *   <li>report → /api/v1/report/**</li>
 *   <li>workflow → /api/v1/workflow/** (样例轻状态机位于 /api/v1/sample/** 不受影响)</li>
 *   <li>datasource → /api/v1/admin/datasources/**</li>
 *   <li>plugin → /api/v1/plugins/** 和 /api/v1/market/** (v1.1+ 预留)</li>
 * </ul>
 *
 * <p>校验流程 (70 号文档「授权校验流程 - 请求校验」):
 * <ol>
 *   <li>根据请求路径匹配 moduleCode</li>
 *   <li>调用 {@link LicenseService#hasModule(String)} 双重校验 (License 授权 && yutong.modules 开关)</li>
 *   <li>未授权时写授权审计日志 ({@link LocalLicenseService#writeAudit})，抛 BusinessException(LIC_MODULE_DENIED)</li>
 * </ol>
 *
 * <p>backendBoundary (70 号文档): 后端拦截是强制边界，前端菜单隐藏只是体验优化。
 * 即使前端菜单被绕过，直连 API 也会被本拦截器拦截。
 *
 * <p>auditRule (70 号文档): 所有直连 API 拒绝必须写授权审计，
 * 包含 moduleCode、edition、licenseId、userIdHash、tenantIdHash、traceId。
 *
 * <p>GA2-L171 落地: 关闭 DEV-L164-004 偏差（6 模块 API 拦截器尚未实现）。
 */
public class LicenseInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(LicenseInterceptor.class);

    /** Ant 风格路径匹配器 (Spring 内置，线程安全) */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    /**
     * 商业模块路径规则 (有序，LinkedHashMap 保证遍历顺序稳定)。
     * key = Ant 路径模式，value = 模块编码。
     *
     * <p>规则对齐 contracts/governance/commercial-license-trimming.yaml#moduleAuthBehaviorMatrix。
     */
    private static final Map<String, String> MODULE_PATH_RULES = new LinkedHashMap<>();

    static {
        // lowcode: 拦截 /api/v1/lowcode/**
        MODULE_PATH_RULES.put("/api/v1/lowcode/**", "lowcode");
        // ai: 拦截 /api/v1/ai/** 新调用; /api/v1/ai-governance/** 属于 AI 治理域
        MODULE_PATH_RULES.put("/api/v1/ai/**", "ai");
        MODULE_PATH_RULES.put("/api/v1/ai-governance/**", "ai");
        // report: 拦截 report API
        MODULE_PATH_RULES.put("/api/v1/report/**", "report");
        // workflow: 拦截流程设计 API; 样例轻状态机 (/api/v1/sample/**) 不受影响
        MODULE_PATH_RULES.put("/api/v1/workflow/**", "workflow");
        // datasource: 拦截 /api/v1/admin/datasources/**
        MODULE_PATH_RULES.put("/api/v1/admin/datasources/**", "datasource");
        // plugin/market: 禁止安装/启用/调用未授权插件 (v1.1+ 预留路径)
        MODULE_PATH_RULES.put("/api/v1/plugins/**", "plugin");
        MODULE_PATH_RULES.put("/api/v1/market/**", "plugin");
    }

    /** 所有需要拦截的路径模式列表 (供 WebMvcConfig 注册 addPathPatterns 使用) */
    public static final List<String> INTERCEPT_PATH_PATTERNS = List.copyOf(MODULE_PATH_RULES.keySet());

    private final LicenseService licenseService;
    private final LocalLicenseService localLicenseService;

    public LicenseInterceptor(LicenseService licenseService, LocalLicenseService localLicenseService) {
        this.licenseService = licenseService;
        this.localLicenseService = localLicenseService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String requestPath = request.getRequestURI();
        String moduleCode = resolveModuleCode(requestPath);
        if (moduleCode == null) {
            // 不属于商业模块路径，放行 (不应发生，WebMvcConfig 只注册了商业路径)
            return true;
        }

        boolean authorized;
        try {
            authorized = licenseService.hasModule(moduleCode);
        } catch (Exception e) {
            // License 校验异常时记录审计并拒绝 (fail-closed 策略，70 号文档「失败与降级策略」)
            log.warn("License 校验异常 moduleCode={} path={} - {}", moduleCode, requestPath, e.getMessage());
            writeDeniedAudit(moduleCode, "LIC-500001",
                    "{\"reason\":\"license_check_error\",\"error\":\"" + escape(e.getMessage()) + "\"}");
            throw new BusinessException(ErrorCode.LIC_INTERNAL_ERROR,
                    "授权校验异常: moduleCode=" + moduleCode);
        }

        if (!authorized) {
            String detail = String.format(
                    "{\"moduleCode\":\"%s\",\"path\":\"%s\",\"method\":\"%s\"}",
                    moduleCode, escape(requestPath), request.getMethod());
            writeDeniedAudit(moduleCode, ErrorCode.LIC_MODULE_DENIED.code(), detail);
            log.warn("模块未授权拦截 moduleCode={} path={} method={} traceId={}",
                    moduleCode, requestPath, request.getMethod(), TraceContext.getTraceId());
            throw new BusinessException(ErrorCode.LIC_MODULE_DENIED,
                    "模块未授权: " + moduleCode + " (当前版本未授权此能力，请联系管理员)");
        }

        return true;
    }

    /** 根据请求路径匹配 moduleCode，无匹配返回 null */
    private String resolveModuleCode(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, String> entry : MODULE_PATH_RULES.entrySet()) {
            if (PATH_MATCHER.match(entry.getKey(), path)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** 写入授权拒绝审计日志 (fail-tolerant，写入失败不阻断) */
    private void writeDeniedAudit(String moduleCode, String errorCode, String detailJson) {
        try {
            // action=DENY 对齐 sys_license_audit_log.ck_sys_license_audit_action 约束
            localLicenseService.writeAudit("DENY", moduleCode, null, "DENIED", errorCode, detailJson);
        } catch (Exception ignored) {
            // 审计写入失败不阻断拦截流程 (LocalLicenseService.writeAudit 内部已容错)
        }
    }

    /** 简单 JSON 字符串转义，避免 detail 中包含双引号破坏 JSON 结构 */
    private static String escape(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
