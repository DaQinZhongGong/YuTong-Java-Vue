package com.yutong.api.facade;

import com.yutong.api.dto.CreateTodoCommand;
import com.yutong.api.dto.PublishMessageCommand;

/**
 * 消息 Facade — 跨模块调用待办/站内信的统一契约。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (boot/cloud Facade 装配规范)
 *
 * <p>装配规则:
 * <ul>
 *   <li>boot 模式: {@code LocalMessageFacade} (@Profile("boot")) 本地 Bean，直接调用 system 模块 Service</li>
 *   <li>cloud 模式: {@code FeignMessageFacade} (@Profile("cloud")) 通过 Feign 调用 system-service</li>
 *   <li>profile 互斥，业务代码只依赖本接口，不感知部署形态</li>
 * </ul>
 *
 * <p>禁止: boot 模式通过 HTTP 调自己；cloud 模式直接跨库访问其他服务表 (98 号文档阻断清单)。
 *
 * <p>使用方: yutong-sample-service 提交申请单后通过本 Facade 创建审批人待办和申请人站内信。
 */
public interface MessageFacade {

    /**
     * 创建待办任务。
     * 由 system 模块的 TodoService 落库，自动填充 tenantId/traceId 等上下文字段。
     *
     * @param command 创建待办命令
     */
    void createTodo(CreateTodoCommand command);

    /**
     * 发布站内信。
     * 由 system 模块的 MessageService 落库，自动填充 tenantId/traceId 等上下文字段。
     *
     * @param command 发布站内信命令
     */
    void publishMessage(PublishMessageCommand command);

    /**
     * 取消某业务单据的所有 PENDING 待办。
     * 申请单撤回/作废时由 sample 模块调用。
     *
     * @param bizType 业务类型
     * @param bizId   业务 ID
     */
    void cancelTodoByBiz(String bizType, String bizId);
}
