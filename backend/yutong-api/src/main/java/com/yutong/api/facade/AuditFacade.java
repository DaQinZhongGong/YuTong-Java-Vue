package com.yutong.api.facade;

import com.yutong.api.dto.AuditRecordCommand;

/**
 * 审计 Facade — 跨模块调用审计日志的统一契约。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (boot/cloud Facade 装配规范)、
 * 67-数据权限与审计日志详设 (审计策略)
 *
 * <p>装配规则:
 * <ul>
 *   <li>boot 模式: {@code LocalAuditFacade} (@Profile("boot")) 本地 Bean，直接调用 system 模块 OperationLogService</li>
 *   <li>cloud 模式: {@code FeignAuditFacade} (@Profile("cloud")) 通过 Feign 调用 system-service</li>
 * </ul>
 *
 * <p>使用方: yutong-sample-service/lowcode-service/ai-service 写业务审计日志时调用本 Facade，
 * 避免直接依赖 system 模块的 Mapper/Service 实现 (98 号文档: 不绕过系统审计 Facade)。
 *
 * <p>容错: 审计写入失败不阻断业务流程 (67 号文档审计写入与业务解耦要求)。
 */
public interface AuditFacade {

    /**
     * 写入审计日志。自动填充 tenantId/operatorId/traceId/ip/userAgent 等上下文字段。
     *
     * @param command 审计记录命令
     */
    void record(AuditRecordCommand command);
}
