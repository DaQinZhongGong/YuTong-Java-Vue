package com.yutong.system.log.facade;

import com.yutong.api.dto.AuditRecordCommand;
import com.yutong.api.facade.AuditFacade;
import com.yutong.system.log.service.OperationLogService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * {@link AuditFacade} 的 boot 模式本地实现。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (boot/cloud Facade 装配规范)、
 * 67-数据权限与审计日志详设 (审计策略)
 *
 * <p>装配规则:
 * <ul>
 *   <li>{@code @Profile("!cloud")} — 所有非 cloud profile (local/test/prod 单体) 均启用</li>
 *   <li>{@code @Service} — Spring 自动扫描注册为 Bean</li>
 *   <li>cloud 模式由 {@code FeignAuditFacade} 接管</li>
 * </ul>
 *
 * <p>职责: 委托给 {@link OperationLogService#record} 写入审计日志，
 * 自动填充 tenantId/operatorId/traceId/ip/userAgent 等上下文字段 (由 OperationLogService 内部完成)。
 *
 * <p>容错: {@link OperationLogService#record} 内部已 try-catch，审计写入失败不阻断业务流程。
 */
@Service
@Profile("!cloud")
public class LocalAuditFacade implements AuditFacade {

    private final OperationLogService operationLogService;

    public LocalAuditFacade(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Override
    public void record(AuditRecordCommand command) {
        operationLogService.record(
                command.operationType(),
                command.module(),
                command.bizType(),
                command.bizId(),
                command.content(),
                command.beforeJson(),
                command.afterJson(),
                command.result(),
                command.errorCode()
        );
    }
}
