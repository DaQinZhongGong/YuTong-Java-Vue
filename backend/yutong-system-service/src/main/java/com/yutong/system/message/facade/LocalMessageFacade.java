package com.yutong.system.message.facade;

import com.yutong.api.dto.CreateTodoCommand;
import com.yutong.api.dto.PublishMessageCommand;
import com.yutong.api.facade.MessageFacade;
import com.yutong.common.id.IdGenerator;
import com.yutong.system.message.domain.SysMessage;
import com.yutong.system.message.domain.SysTodoTask;
import com.yutong.system.message.service.MessageService;
import com.yutong.system.message.service.TodoService;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * {@link MessageFacade} 的 boot 模式本地实现。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (boot/cloud Facade 装配规范)
 *
 * <p>装配规则:
 * <ul>
 *   <li>{@code @Profile("!cloud")} — 所有非 cloud profile (local/test/prod 单体) 均启用</li>
 *   <li>{@code @Service} — Spring 自动扫描注册为 Bean，业务代码通过 {@link MessageFacade} 接口注入</li>
 *   <li>cloud 模式不注册本 Bean，由 {@code FeignMessageFacade} 接管 (后续 cloud 形态补充)</li>
 * </ul>
 *
 * <p>职责: 将 Facade DTO 转换为 system 模块实体，委托给 TodoService/MessageService 落库。
 * 自动填充 id (ULID)；tenantId/createdBy/createdTime 由 MetaObjectHandler 自动填充。
 *
 * <p>禁止: boot 模式通过 HTTP 调自己 (98 号文档阻断清单)。
 */
@Service
@Profile("!cloud")
public class LocalMessageFacade implements MessageFacade {

    private final TodoService todoService;
    private final MessageService messageService;

    public LocalMessageFacade(TodoService todoService, MessageService messageService) {
        this.todoService = todoService;
        this.messageService = messageService;
    }

    @Override
    public void createTodo(CreateTodoCommand command) {
        SysTodoTask todo = new SysTodoTask();
        todo.setId(IdGenerator.nextId());
        todo.setTodoType(command.todoType() != null ? command.todoType() : "HANDLE");
        todo.setBizType(command.bizType());
        todo.setBizId(command.bizId());
        todo.setTitle(command.title());
        todo.setAssigneeId(command.assigneeId());
        todo.setTodoStatus("PENDING");
        // content 字段 sys_todo_task 表无对应列，存入 remark (BaseEntity 提供)
        if (command.content() != null && !command.content().isBlank()) {
            todo.setRemark(command.content());
        }
        // redirectUrl 暂存 sourceEventId 字段 (语义近似：来源标识)
        // 后续如需精确路由跳转，可扩展 sys_todo_task 增加路由字段
        if (command.redirectUrl() != null && !command.redirectUrl().isBlank()) {
            todo.setSourceEventId(command.redirectUrl());
        }
        todoService.createTodo(todo);
    }

    @Override
    public void publishMessage(PublishMessageCommand command) {
        SysMessage message = new SysMessage();
        message.setId(IdGenerator.nextId());
        message.setReceiverId(command.receiverId());
        message.setMsgType(command.messageType() != null ? command.messageType() : "SYSTEM");
        message.setTitle(command.title());
        message.setContent(command.content());
        message.setReadStatus("UNREAD");
        message.setBizType(command.bizType());
        message.setBizId(command.bizId());
        // redirectUrl 映射到 targetRouteId (前端路由 ID)
        if (command.redirectUrl() != null && !command.redirectUrl().isBlank()) {
            message.setTargetRouteId(command.redirectUrl());
        }
        messageService.createMessage(message);
    }

    @Override
    public void cancelTodoByBiz(String bizType, String bizId) {
        todoService.cancelTodoByBiz(bizType, bizId);
    }
}
