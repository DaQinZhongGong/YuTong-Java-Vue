package com.yutong.boot.config;

import cn.dev33.satoken.exception.NotLoginException;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.DataScopeDeniedException;
import com.yutong.common.exception.PermissionDeniedException;
import com.yutong.common.response.Result;
import com.yutong.common.trace.TraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.yutong.system.log.service.OperationLogService;

/**
 * 全局异常处理。设计来源: 98-后端实现蓝图与代码骨架详设 GlobalExceptionHandler (10 种异常映射表)、
 * 43-国际化与无障碍设计、67-数据权限与审计日志详设 (越权审计策略)
 *
 * <p>按 Accept-Language 解析 messageKey 得到本地化 message，填充 Result.messageKey 字段。
 *
 * <p>异常覆盖 (98 号文档要求 10 种):
 * <ol>
 *   <li>MethodArgumentNotValidException → 400 SYS-400001</li>
 *   <li>NotLoginException (Sa-Token) → 401 SYS-401001 (登录态可写安全日志)</li>
 *   <li>PermissionDeniedException → 403 AUTH-403001 (写越权审计)</li>
 *   <li>DataScopeDeniedException → 403 AUTH-403002 (必写越权审计)</li>
 *   <li>OptimisticLockingFailureException → 409 SYS-409001</li>
 *   <li>BusinessException (含 ResourceNotFoundException/BusinessConflictException/
 *       IdempotencyProcessingException/IdempotencyConflictException 等) → 各 errorCode 对应 HTTP</li>
 *   <li>Throwable → 500 SYS-500001 (错误日志)</li>
 * </ol>
 *
 * <p>注: BadCredentialsException (AUTH-401001 写 sys_login_log) 当前未引入 Spring Security，
 * 登录失败由 AuthAdapter 抛 BusinessException(AUTH_LOGIN_FAILED) 走 BusinessException 通用分支，
 * 后续接入 Spring Security 时补充专用 handler。
 *
 * <p>GA2-31 修复: 所有 handler 返回的 ResponseEntity 显式设置 Content-Type: application/json。
 * 原因: SSE 端点 (POST /ai/chat, Accept: text/event-stream) 在鉴权失败时抛 PermissionDeniedException,
 * GlobalExceptionHandler 返回 ResponseEntity&lt;Result&lt;Void&gt;&gt; (JSON), 但客户端 Accept 头限制
 * 导致 Spring MVC 找不到能将 Result 序列化为 text/event-stream 的 HttpMessageConverter,
 * 抛 HttpMediaTypeNotAcceptableException, 最终返回 500。显式设置 Content-Type 覆盖 Accept 限制。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 审计模块标识，对齐 67 号文档 module 字段。 */
    private static final String AUDIT_MODULE_SECURITY = "security";
    /** 审计操作类型，对齐 67 号文档 operation_type 字段。 */
    private static final String AUDIT_OP_DENIED = "DENIED";
    /** 审计结果，对齐 67 号文档 result 字段。 */
    private static final String AUDIT_RESULT_DENIED = "DENIED";

    private final MessageSource messageSource;
    private final OperationLogService operationLogService;

    public GlobalExceptionHandler(MessageSource messageSource,
                                  OperationLogService operationLogService) {
        this.messageSource = messageSource;
        this.operationLogService = operationLogService;
    }

    /**
     * 1. 参数校验失败 → 400 SYS-400001。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError fe = ex.getBindingResult().getFieldError();
        String detail = fe != null ? fe.getField() + ": " + fe.getDefaultMessage() : null;
        ErrorCode ec = ErrorCode.SYS_PARAM_INVALID;
        String message = resolveMessage(ec.messageKey(), detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ec.code(), message, ec.messageKey(), null, TraceContext.getTraceId()));
    }

    /**
     * 1b. 必填请求参数缺失 → 400 SYS-400002。
     * Spring @RequestParam(required=true) 缺失时抛出此异常，
     * 需在此显式处理，否则会被 Throwable 兜底误返回 500。
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> handleMissingParam(MissingServletRequestParameterException ex) {
        ErrorCode ec = ErrorCode.SYS_PARAM_MISSING;
        String detail = ex.getParameterName() + ": required parameter is missing";
        String message = resolveMessage(ec.messageKey(), detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ec.code(), message, ec.messageKey(), null, TraceContext.getTraceId()));
    }

    /**
     * 2. 未登录或 token 失效 (Sa-Token) → 401 SYS-401001。
     * 登录态可写安全日志 (98 号文档审计策略)。
     */
    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Result<Void>> handleNotLogin(NotLoginException ex) {
        log.warn("未登录访问 traceId={} type={} - {}",
                TraceContext.getTraceId(), ex.getType(), ex.getMessage());
        ErrorCode ec = ErrorCode.SYS_UNAUTHORIZED;
        String message = resolveMessage(ec.messageKey(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ec.code(), message, ec.messageKey(), null, TraceContext.getTraceId()));
    }

    /**
     * 3. 权限码不足 → 403 AUTH-403001。
     * 写越权审计 (98 号文档审计策略)。
     */
    @ExceptionHandler(PermissionDeniedException.class)
    public ResponseEntity<Result<Void>> handlePermissionDenied(PermissionDeniedException ex) {
        ErrorCode ec = ex.errorCode();
        String message = resolveMessage(ec.messageKey(), ex.customMessage());
        writeSecurityAudit("PERMISSION_DENIED", ec.code(), ex.customMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ec.code(), message, ec.messageKey(), null, TraceContext.getTraceId()));
    }

    /**
     * 4. DataScope 拒绝 → 403 AUTH-403002。
     * 必写越权审计 (98 号文档审计策略，强制项)。
     */
    @ExceptionHandler(DataScopeDeniedException.class)
    public ResponseEntity<Result<Void>> handleDataScopeDenied(DataScopeDeniedException ex) {
        ErrorCode ec = ex.errorCode();
        String message = resolveMessage(ec.messageKey(), ex.customMessage());
        writeSecurityAudit("DATA_SCOPE_DENIED", ec.code(), ex.customMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ec.code(), message, ec.messageKey(), null, TraceContext.getTraceId()));
    }

    /**
     * 5. 乐观锁冲突 → 409 SYS-409001。
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Result<Void>> handleOptimisticLock(OptimisticLockingFailureException ex) {
        log.warn("乐观锁冲突 traceId={} - {}", TraceContext.getTraceId(), ex.getMessage());
        ErrorCode ec = ErrorCode.SYS_OPTIMISTIC_LOCK;
        String message = resolveMessage(ec.messageKey(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ec.code(), message, ec.messageKey(), null, TraceContext.getTraceId()));
    }

    /**
     * 6. 业务异常通用分支 (含 ResourceNotFoundException/BusinessConflictException/
     * IdempotencyProcessingException/IdempotencyConflictException 等)。
     * Spring 自动匹配最具体的 handler，上述专用 handler 优先于此分支。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException ex) {
        ErrorCode ec = ex.errorCode();
        // customMessage 优先（含上下文详情），兜底走 i18n messageKey
        String message = resolveMessage(ec.messageKey(), ex.customMessage());
        return ResponseEntity.status(ec.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ec.code(), message, ec.messageKey(), null, TraceContext.getTraceId()));
    }

    /**
     * 7. 未映射 URL 路径 (Spring Boot 3.2+ 静态资源/404) → 404 SYS-404001。
     * 避免 NoResourceFoundException 落入 Throwable 兜底误返回 500。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException ex) {
        ErrorCode ec = ErrorCode.SYS_NOT_FOUND;
        String message = resolveMessage(ec.messageKey(), ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ec.code(), message, ec.messageKey(), null, TraceContext.getTraceId()));
    }

    /**
     * 8. 兜底异常 → 500 SYS-500001 (错误日志)。
     */
    @ExceptionHandler(Throwable.class)
    public ResponseEntity<Result<Void>> handleThrowable(Throwable ex) {
        log.error("系统异常 traceId={}", TraceContext.getTraceId(), ex);
        ErrorCode ec = ErrorCode.SYS_INTERNAL_ERROR;
        String message = resolveMessage(ec.messageKey(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.fail(ec.code(), message, ec.messageKey(), null, TraceContext.getTraceId()));
    }

    /**
     * 写入越权审计日志。PermissionDeniedException/DataScopeDeniedException 触发。
     * OperationLogService.record 内部已 try-catch，写入失败不影响主流程。
     */
    private void writeSecurityAudit(String deniedType, String errorCode, String detail) {
        try {
            String content = deniedType + (detail != null ? ": " + detail : "");
            operationLogService.record(
                    AUDIT_OP_DENIED,
                    AUDIT_MODULE_SECURITY,
                    "",       // bizType 留空，越权审计不绑定特定业务
                    null,     // bizId 留空
                    content,
                    null,     // beforeJson
                    null,     // afterJson
                    AUDIT_RESULT_DENIED,
                    errorCode
            );
        } catch (Exception e) {
            // 审计写入失败不阻断异常响应流程
            log.error("越权审计写入失败 deniedType={} errorCode={} - {}",
                    deniedType, errorCode, e.getMessage(), e);
        }
    }

    /**
     * 解析本地化消息。优先级: MessageSource 解析结果 > fallback > messageKey 本身。
     * 当 MessageSource 返回的消息与 key 相同（useCodeAsDefaultMessage），视为未找到，改用 fallback。
     */
    private String resolveMessage(String messageKey, String fallback) {
        try {
            String resolved = messageSource.getMessage(messageKey, null, LocaleContextHolder.getLocale());
            if (resolved != null && !resolved.equals(messageKey)) {
                return resolved;
            }
        } catch (Exception ignored) {
            // MessageSource 解析失败，使用兜底
        }
        return fallback != null ? fallback : messageKey;
    }
}
