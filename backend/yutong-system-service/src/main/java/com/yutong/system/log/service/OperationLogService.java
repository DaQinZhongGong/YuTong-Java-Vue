package com.yutong.system.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.mask.FieldMaskingService;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.log.domain.SysOperationLog;
import com.yutong.system.log.mapper.SysOperationLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.OffsetDateTime;

/**
 * 操作日志服务。设计来源: 67-数据权限与审计日志详设、98-后端实现蓝图系统基础接口补齐规则
 *
 * <p>提供两类能力:
 * <ol>
 *   <li>查询: {@link #pageLogs} / {@link #getLog} 供管理端审计日志页面使用</li>
 *   <li>写入: {@link #record} 供 {@code AuditableAspect} 在 AOP 切面中自动调用，
 *       记录业务操作的 before/after 快照、操作人、链路 ID 等审计信息</li>
 * </ol>
 *
 * <p>GA2-09-1: 新增 {@link #record} 方法，实现 @Auditable 注解驱动自动审计。
 * 自动从 {@link CurrentUserContext} 填充 tenantId/operatorId/operatorName，
 * 从 {@link TraceContext} 填充 traceId，
 * 从 {@link HttpServletRequest} 填充 ip/userAgent。
 *
 * <p>异常容错: 审计日志写入失败不阻断业务流程，仅记录 ERROR 日志，
 * 避免审计组件故障影响业务可用性 (67 号文档审计写入与业务解耦要求)。
 */
@Service
public class OperationLogService {

    private static final Logger log = LoggerFactory.getLogger(OperationLogService.class);

    /** 审计日志写入失败时的兜底日志前缀。 */
    private static final String AUDIT_WRITE_FAILURE = "审计日志写入失败 operationType={} bizId={} - ";

    private final SysOperationLogMapper operationLogMapper;
    private final ObjectMapper objectMapper;

    public OperationLogService(SysOperationLogMapper operationLogMapper, ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询操作日志，按 operatorId/bizType/bizId 过滤，keyword 模糊匹配 content，按 operatedTime DESC。
     *
     * <p>GA2-L176: 新增 bizType/bizId 过滤 (67 号文档 line 146
     * "按业务对象查询使用 /api/v1/operation-logs?bizType=...&bizId=...")。
     *
     * <p>GA2-L176: 返回前对 beforeJson/afterJson 做深度递归脱敏 (67 号文档 line 100-101
     * "before_json 变更前脱敏快照 / after_json 变更后脱敏快照")。
     */
    public PageResult<SysOperationLog> pageLogs(PageRequest request, String operatorId, String keyword,
                                                 String result, String bizType, String bizId) {
        LambdaQueryWrapper<SysOperationLog> wrapper = new LambdaQueryWrapper<SysOperationLog>()
                .eq(SysOperationLog::getTenantId, CurrentUserContext.getTenantId())
                .eq(operatorId != null && !operatorId.isBlank(), SysOperationLog::getOperatorId, operatorId)
                .like(keyword != null && !keyword.isBlank(), SysOperationLog::getContent, keyword)
                .eq(result != null && !result.isBlank(), SysOperationLog::getResult, result)
                .eq(bizType != null && !bizType.isBlank(), SysOperationLog::getBizType, bizType)
                .eq(bizId != null && !bizId.isBlank(), SysOperationLog::getBizId, bizId)
                .orderByDesc(SysOperationLog::getOperatedTime);
        Page<SysOperationLog> page = operationLogMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        page.getRecords().forEach(this::applyMasking);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /** 查询操作日志详情。返回前对 beforeJson/afterJson 做深度递归脱敏。 */
    public SysOperationLog getLog(String id) {
        SysOperationLog log = operationLogMapper.selectById(id);
        if (log == null) throw new ResourceNotFoundException("操作日志不存在: " + id);
        applyMasking(log);
        return log;
    }

    /**
     * GA2-L176: 对操作日志的 beforeJson/afterJson 做深度递归脱敏。
     * 67 号文档 line 100-101: before_json/after_json 默认脱敏。
     * 使用 Jackson 遍历 JSON 节点，对敏感字段名 (password/secret/token/authorization/api_key/phone/email)
     * 调用 {@link FieldMaskingService#maskByFieldName} 脱敏。
     */
    private void applyMasking(SysOperationLog log) {
        log.setBeforeJson(maskJsonDeep(log.getBeforeJson()));
        log.setAfterJson(maskJsonDeep(log.getAfterJson()));
    }

    /**
     * 深度递归脱敏 JSON 字符串。对 Object 节点的每个字段名判断是否需要脱敏，
     * 对 Array 节点递归处理每个元素。
     * 解析失败时回退为原值 (不阻断查询)。
     */
    private String maskJsonDeep(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            JsonNode masked = maskNodeDeep(node);
            return objectMapper.writeValueAsString(masked);
        } catch (Exception e) {
            // JSON 解析失败，回退为原值 (可能不是合法 JSON 或是简单字符串)
            log.debug("JSON 脱敏解析失败，回退原值: {}", e.getMessage());
            return json;
        }
    }

    /** 递归处理 JsonNode，对 ObjectNode 的字段名做脱敏判断。 */
    private JsonNode maskNodeDeep(JsonNode node) {
        if (node == null || node.isNull() || node.isValueNode()) {
            return node;
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, maskNodeDeep(node.get(i)));
            }
            return node;
        }
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            // 收集需要替换的字段名 (避免遍历时修改)
            java.util.List<String> fieldNames = new java.util.ArrayList<>();
            obj.fieldNames().forEachRemaining(fieldNames::add);
            for (String fieldName : fieldNames) {
                JsonNode child = obj.get(fieldName);
                if (child != null && child.isTextual()) {
                    String masked = FieldMaskingService.maskByFieldName(fieldName, child.asText());
                    if (!masked.equals(child.asText())) {
                        obj.put(fieldName, masked);
                    }
                } else if (child != null && (child.isObject() || child.isArray())) {
                    obj.set(fieldName, maskNodeDeep(child));
                }
            }
            return obj;
        }
        return node;
    }

    /**
     * 写入操作审计日志。由 {@code AuditableAspect} 在业务方法执行后调用。
     *
     * <p>自动填充字段:
     * <ul>
     *   <li>id: ULID</li>
     *   <li>tenantId/operatorId/operatorName: 来自 {@link CurrentUserContext}</li>
     *   <li>traceId: 来自 {@link TraceContext}</li>
     *   <li>ip/userAgent: 来自当前 {@link HttpServletRequest}</li>
     *   <li>operatedTime: 当前时间</li>
     * </ul>
     *
     * <p>容错策略: 任何异常被 catch 后仅记录 ERROR 日志，不向上抛出，
     * 确保审计日志写入失败不影响业务事务。
     *
     * @param operationType 操作类型 (CREATE/UPDATE/DELETE/SUBMIT/APPROVE/REJECT/WITHDRAW/ARCHIVE 等)
     * @param module        模块 (system/sample/lowcode/ai 等)
     * @param bizType       业务类型 (如 biz_request)
     * @param bizId         业务 ID (可空，CREATE 场景可能无值)
     * @param content       操作摘要 (可空)
     * @param beforeJson    变更前快照 JSON (可空)
     * @param afterJson     变更后快照 JSON (可空)
     * @param result        结果 (SUCCESS/FAILED/DENIED)
     * @param errorCode     错误码 (FAILED 时填写，可空)
     */
    public void record(String operationType, String module, String bizType, String bizId,
                       String content, String beforeJson, String afterJson,
                       String result, String errorCode) {
        try {
            SysOperationLog entity = new SysOperationLog();
            entity.setId(IdGenerator.nextId());
            entity.setTenantId(safe(CurrentUserContext.getTenantId()));
            entity.setOperationType(operationType);
            entity.setModule(module);
            entity.setBizType(bizType);
            entity.setBizId(bizId);
            entity.setContent(content);
            entity.setBeforeJson(beforeJson);
            entity.setAfterJson(afterJson);
            entity.setResult(result);
            entity.setErrorCode(errorCode);
            entity.setTraceId(TraceContext.getTraceId());
            entity.setOperatorId(safe(CurrentUserContext.getUserId()));
            entity.setOperatorName(CurrentUserContext.getUsername());
            entity.setOperatedTime(OffsetDateTime.now());

            // 从当前 HTTP 请求填充 ip/userAgent（异步或非 Web 场景为 null）
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                entity.setIp(resolveClientIp(request));
                String ua = request.getHeader("User-Agent");
                entity.setUserAgent(truncate(ua, 512));
            }

            operationLogMapper.insert(entity);
        } catch (Exception e) {
            // 审计日志写入失败不影响业务流程，仅记录错误日志
            log.error(AUDIT_WRITE_FAILURE + e.getMessage(), operationType, bizId, e);
        }
    }

    /**
     * 解析客户端真实 IP。优先取 X-Forwarded-For / X-Real-IP（反向代理场景），回退到 remoteAddr。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            // X-Forwarded-For 可能包含多个 IP，取第一个（最原始客户端）
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    /** 字符串截断保护，防止超长字段写入数据库失败。 */
    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    /** null 安全处理，避免 NOT NULL 字段写入 null 报错。 */
    private String safe(String value) {
        return value != null ? value : "";
    }
}
