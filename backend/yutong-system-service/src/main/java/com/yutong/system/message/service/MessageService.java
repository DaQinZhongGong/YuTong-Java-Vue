package com.yutong.system.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.AuthAdapter;
import com.yutong.auth.AuthContext;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.response.PageRequest;
import com.yutong.common.response.PageResult;
import com.yutong.system.message.domain.SysMessage;
import com.yutong.system.message.mapper.SysMessageMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 站内信服务。设计来源: 98-后端实现蓝图系统基础接口补齐规则
 * 查询范围限定当前用户(receiver_id=当前用户)，按 created_time desc 排序。
 *
 * <p>GA2-DS: pageMessages 接入 DataScope 数据权限过滤。
 * permissions.yaml shared.messages.list 策略: admin 读全部，普通用户读自己的。
 * admin (有 system:message:read-all 或 * 权限) 不追加 receiver_id 过滤，可读全部站内信。
 */
@Service
public class MessageService {

    /** 站内信资源编码，对齐 permissions.yaml shared.messages.list 策略。 */
    public static final String RESOURCE_CODE = "sys:message";
    /** 全量读取站内信权限码 (admin 才授予)，对齐 permissions.yaml message 域权限码定义。 */
    public static final String PERM_READ_ALL = "system:message:read-all";

    private final SysMessageMapper messageMapper;
    private final AuthAdapter authAdapter;

    public MessageService(SysMessageMapper messageMapper, AuthAdapter authAdapter) {
        this.messageMapper = messageMapper;
        this.authAdapter = authAdapter;
    }

    /**
     * 分页查询站内信，按 created_time desc。
     * GA2-DS: 默认按 receiver_id = currentUserId 过滤；admin (有 system:message:read-all 或 * 权限) 不追加过滤，可读全部。
     */
    public PageResult<SysMessage> pageMessages(PageRequest request, String readStatus) {
        // GA2-DS: 接入 DataScope 数据权限过滤 (shared.messages.list 策略)
        boolean canReadAll = canReadAllMessages();
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, CurrentUserContext.getTenantId())
                .eq(!canReadAll, SysMessage::getReceiverId, CurrentUserContext.getUserId())
                .eq(readStatus != null && !readStatus.isBlank(), SysMessage::getReadStatus, readStatus)
                .orderByDesc(SysMessage::getCreatedTime);
        Page<SysMessage> page = messageMapper.selectPage(
                new Page<>(request.page(), request.size()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), request.page(), request.size());
    }

    /**
     * GA2-DS: 判断当前用户是否可读全部站内信。
     * admin (hasPermission * 通配) 或拥有 system:message:read-all 权限的用户可读全部，其余用户仅能读自己的。
     */
    private boolean canReadAllMessages() {
        AuthContext ctx = authAdapter.current();
        return ctx != null
                && (ctx.hasPermission("*") || ctx.hasPermission(PERM_READ_ALL));
    }

    /** 当前用户未读消息数。 */
    public long getUnreadCount() {
        LambdaQueryWrapper<SysMessage> wrapper = new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, CurrentUserContext.getTenantId())
                .eq(SysMessage::getReceiverId, CurrentUserContext.getUserId())
                .eq(SysMessage::getReadStatus, "UNREAD");
        Long count = messageMapper.selectCount(wrapper);
        return count != null ? count : 0L;
    }

    /** 创建站内信(内部调用，供申请单事件触发)。 */
    @Transactional
    public SysMessage createMessage(SysMessage message) {
        messageMapper.insert(message);
        return message;
    }

    /** 标记已读，设置 readTime。检查消息存在且属于当前用户。 */
    @Transactional
    public void markAsRead(String id) {
        SysMessage existing = messageMapper.selectById(id);
        if (existing == null || !CurrentUserContext.getUserId().equals(existing.getReceiverId())) {
            throw new ResourceNotFoundException("消息不存在: " + id);
        }
        existing.setReadStatus("READ");
        existing.setReadTime(OffsetDateTime.now());
        messageMapper.updateById(existing);
    }

    /** 当前用户全部已读。 */
    @Transactional
    public void markAllRead() {
        LambdaUpdateWrapper<SysMessage> wrapper = new LambdaUpdateWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, CurrentUserContext.getTenantId())
                .eq(SysMessage::getReceiverId, CurrentUserContext.getUserId())
                .eq(SysMessage::getReadStatus, "UNREAD")
                .set(SysMessage::getReadStatus, "READ")
                .set(SysMessage::getReadTime, OffsetDateTime.now());
        messageMapper.update(null, wrapper);
    }
}
