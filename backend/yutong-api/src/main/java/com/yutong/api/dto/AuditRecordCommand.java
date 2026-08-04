package com.yutong.api.dto;

/**
 * 审计记录命令。跨模块调用 AuditFacade.record 时使用。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (boot/cloud Facade 装配规范)、
 * 67-数据权限与审计日志详设 (审计策略)
 *
 * <p>字段对齐 sys_operation_log 表，由 LocalAuditFacade 转换为 SysOperationLog 实体。
 * 适用于其他模块 (sample/lowcode/ai) 调用 system 模块写审计日志的场景。
 *
 * @param operationType  操作类型 (CREATE/UPDATE/DELETE/SUBMIT/APPROVE/REJECT/WITHDRAW/ARCHIVE)
 * @param module         模块 (system/sample/lowcode/ai/report/workflow/security)
 * @param bizType        业务类型 (如 biz_request)
 * @param bizId          业务 ID (可空)
 * @param content        操作摘要
 * @param beforeJson     变更前快照 JSON (可空)
 * @param afterJson      变更后快照 JSON (可空)
 * @param result         结果 (SUCCESS/FAILED/DENIED)
 * @param errorCode      错误码 (FAILED 时填写，可空)
 */
public record AuditRecordCommand(
        String operationType,
        String module,
        String bizType,
        String bizId,
        String content,
        String beforeJson,
        String afterJson,
        String result,
        String errorCode
) {
}
