package com.yutong.api.dto;

/**
 * 发布站内信命令。跨模块调用 MessageFacade.publishMessage 时使用。
 *
 * <p>设计来源: 98-后端实现蓝图与代码骨架详设 (boot/cloud Facade 装配规范)
 * 字段对齐 sys_message 表，由 LocalMessageFacade 转换为 SysMessage 实体。
 *
 * @param receiverId   接收人 ID
 * @param messageType  消息类型 (如 APPROVE_RESULT/SYSTEM_NOTIFY)
 * @param title        消息标题
 * @param content      消息内容
 * @param bizType      业务类型 (可空)
 * @param bizId        业务 ID (可空)
 * @param redirectUrl  跳转 URL (可空)
 */
public record PublishMessageCommand(
        String receiverId,
        String messageType,
        String title,
        String content,
        String bizType,
        String bizId,
        String redirectUrl
) {
}
