package com.yutong.api.dto;

/**
 * 创建待办命令。跨模块调用 MessageFacade.createTodo 时使用。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (boot/cloud Facade 装配规范)
 * 字段对齐 sys_todo_task 表，由 LocalMessageFacade 转换为 SysTodoTask 实体。
 *
 * @param bizType      业务类型 (如 biz_request)
 * @param bizId        业务 ID
 * @param assigneeId   指派人 ID
 * @param title        待办标题
 * @param content      待办内容
 * @param todoType     待办类型 (如 APPROVE/NOTIFY)
 * @param redirectUrl  跳转 URL (前端路由)
 */
public record CreateTodoCommand(
        String bizType,
        String bizId,
        String assigneeId,
        String title,
        String content,
        String todoType,
        String redirectUrl
) {
}
