package com.yutong.system.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yutong.auth.DataScopeResolver;
import com.yutong.common.auth.CurrentUserContext;
import com.yutong.common.auth.DataScope;
import com.yutong.common.auth.DataScopeType;
import com.yutong.common.errorcode.ErrorCode;
import com.yutong.common.exception.BusinessException;
import com.yutong.common.exception.ResourceNotFoundException;
import com.yutong.common.id.IdGenerator;
import com.yutong.common.response.PageResult;
import com.yutong.common.trace.TraceContext;
import com.yutong.system.message.domain.SysMessage;
import com.yutong.system.message.mapper.SysMessageMapper;
import com.yutong.system.notification.domain.SysMessageTemplate;
import com.yutong.system.notification.domain.SysNotificationDispatchLog;
import com.yutong.system.notification.domain.SysNotificationSubscription;
import com.yutong.system.notification.dto.*;
import com.yutong.system.notification.mapper.SysMessageTemplateMapper;
import com.yutong.system.notification.mapper.SysNotificationDispatchLogMapper;
import com.yutong.system.notification.mapper.SysNotificationSubscriptionMapper;
import com.yutong.system.realtime.dto.RealtimeEnvelope;
import com.yutong.system.realtime.service.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通知运营应用服务。设计来源: 35-样例业务矩阵扩展设计 P2 实时通知 + 44-实时通信与消息推送设计
 * <p>
 * GA2-40 落地: 编排模板渲染 → 站内信入库 → 分发日志 → 实时推送 → 移动订阅推送 完整链路。
 * <p>
 * 6 项核心能力对应：
 * <ul>
 *   <li>站内信: 写 sys_message</li>
 *   <li>未读数: getStats 返回 currentUserUnreadCount + PushService 推送 UNREAD_COUNT 事件</li>
 *   <li>实时推送: PushService.push (复用 GA2-32 WebSocket 通道)</li>
 *   <li>移动端订阅消息: 查 sys_notification_subscription + MockMobilePushAdapter.push</li>
 *   <li>消息模板: sys_message_template + renderTemplate 变量替换</li>
 *   <li>消息重试: sys_notification_dispatch_log + retryDispatch + NotificationRetryScheduler</li>
 * </ul>
 */
@Service
public class NotificationOpsApplicationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOpsApplicationService.class);

    /** 模板变量占位符正则: ${varName} */
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)}");

    /** 通知模板资源编码，对齐 permissions.yaml system:notification:* 命名。 */
    public static final String RESOURCE_CODE = "system:notification";

    private final SysMessageTemplateMapper templateMapper;
    private final SysNotificationDispatchLogMapper dispatchLogMapper;
    private final SysNotificationSubscriptionMapper subscriptionMapper;
    private final SysMessageMapper messageMapper;
    private final PushService pushService;
    private final MockMobilePushAdapter mobilePushAdapter;
    private final DataScopeResolver dataScopeResolver;

    public NotificationOpsApplicationService(SysMessageTemplateMapper templateMapper,
                                             SysNotificationDispatchLogMapper dispatchLogMapper,
                                             SysNotificationSubscriptionMapper subscriptionMapper,
                                             SysMessageMapper messageMapper,
                                             PushService pushService,
                                             MockMobilePushAdapter mobilePushAdapter,
                                             DataScopeResolver dataScopeResolver) {
        this.templateMapper = templateMapper;
        this.dispatchLogMapper = dispatchLogMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.messageMapper = messageMapper;
        this.pushService = pushService;
        this.mobilePushAdapter = mobilePushAdapter;
        this.dataScopeResolver = dataScopeResolver;
    }

    // ==================== 核心能力 1: 发送通知 (编排模板+站内信+分发+推送+移动) ====================

    /**
     * 发送通知。完整编排流程:
     * 1. 加载模板 (按 templateCode + tenant_id)
     * 2. 渲染标题/内容 (变量替换 ${var})
     * 3. 写 sys_message (站内信)
     * 4. 写 sys_notification_dispatch_log (IN_APP, PENDING → SENT)
     * 5. PushService.push (WebSocket 实时推送)
     * 6. 查询用户订阅 (MOBILE_PUSH)
     * 7. 若订阅: 写 MOBILE_PUSH 分发日志 + 调用 MockMobilePushAdapter
     */
    @Transactional
    public SendNotificationVO sendNotification(SendNotificationRequest request) {
        String tenantId = CurrentUserContext.getTenantId();

        // 1. 加载模板
        SysMessageTemplate template = templateMapper.selectOne(new LambdaQueryWrapper<SysMessageTemplate>()
                .eq(SysMessageTemplate::getTenantId, tenantId)
                .eq(SysMessageTemplate::getTemplateCode, request.getTemplateCode())
                .last("LIMIT 1"));
        if (template == null) {
            throw new ResourceNotFoundException(ErrorCode.NOT_TEMPLATE_NOT_FOUND,
                    "模板不存在: " + request.getTemplateCode());
        }
        if (SysMessageTemplate.STATUS_DISABLED.equals(template.getStatus())) {
            throw new BusinessException(ErrorCode.NOT_TEMPLATE_DISABLED,
                    "模板已禁用: " + request.getTemplateCode());
        }

        // 2. 渲染标题/内容
        String title = renderTemplate(template.getTitleTemplate(), request.getVariables());
        String content = renderTemplate(template.getContentTemplate(), request.getVariables());
        log.info("sendNotification: templateCode={} receiverId={} title={} tenantId={}",
                request.getTemplateCode(), request.getReceiverId(), title, tenantId);

        // 3. 写 sys_message (站内信)
        SysMessage message = new SysMessage();
        message.setId(IdGenerator.nextId());
        message.setTenantId(tenantId);
        message.setReceiverId(request.getReceiverId());
        message.setMsgType(template.getMsgType());
        message.setTitle(title);
        message.setContent(content);
        message.setReadStatus("UNREAD");
        message.setBizType(request.getBizType() != null ? request.getBizType() : template.getMsgType());
        message.setBizId(request.getBizId());
        // V002 sys_message.target_route_id 为 NOT NULL, 模板未配置时使用默认路由
        message.setTargetRouteId(template.getTargetRouteId() != null ? template.getTargetRouteId() : "/workbench/todos");
        messageMapper.insert(message);

        // 4. 写 IN_APP 分发日志 (PENDING → SENT)
        List<String> dispatchLogIds = new ArrayList<>();
        String inAppLogId = createDispatchLog(message.getId(), request.getReceiverId(),
                SysNotificationDispatchLog.CHANNEL_IN_APP,
                SysNotificationDispatchLog.DEFAULT_MAX_RETRY,
                SysNotificationDispatchLog.DEFAULT_BACKOFF_MS,
                "{\"channel\":\"IN_APP\",\"title\":\"" + escapeJson(title) + "\"}");
        dispatchLogIds.add(inAppLogId);
        markDispatchSent(inAppLogId);

        // 5. PushService.push (WebSocket 实时推送, 复用 GA2-32 通道)
        boolean realtimePushed = false;
        try {
            RealtimeEnvelope envelope = new RealtimeEnvelope(
                    request.getReceiverId(), tenantId,
                    RealtimeEnvelope.TYPE_NOTIFICATION, template.getTemplateCode(),
                    title, content, message.getBizType(), message.getBizId(),
                    TraceContext.getTraceId());
            envelope.setPersisted(true);
            envelope.setDeliveryMode(RealtimeEnvelope.DELIVERY_PERSIST_THEN_PUSH);
            envelope.setPriority(template.getPriority());
            pushService.push(envelope);
            realtimePushed = true;
        } catch (Exception e) {
            log.warn("sendNotification: 实时推送失败 messageId={} receiverId={} - {}",
                    message.getId(), request.getReceiverId(), e.getMessage());
        }

        // 6. 查询用户移动端订阅 (topic=template.msgType, channel=MOBILE_PUSH)
        boolean mobilePushTriggered = false;
        SysNotificationSubscription sub = subscriptionMapper.selectOne(new LambdaQueryWrapper<SysNotificationSubscription>()
                .eq(SysNotificationSubscription::getTenantId, tenantId)
                .eq(SysNotificationSubscription::getUserId, request.getReceiverId())
                .eq(SysNotificationSubscription::getTopic, template.getMsgType())
                .eq(SysNotificationSubscription::getChannel, SysNotificationSubscription.CHANNEL_MOBILE_PUSH)
                .eq(SysNotificationSubscription::getEnabled, true)
                .last("LIMIT 1"));

        // 7. 若订阅: 写 MOBILE_PUSH 分发日志 + 调用 MockMobilePushAdapter
        if (sub != null) {
            String mobileLogId = createDispatchLog(message.getId(), request.getReceiverId(),
                    SysNotificationDispatchLog.CHANNEL_MOBILE_PUSH,
                    SysNotificationDispatchLog.DEFAULT_MAX_RETRY,
                    SysNotificationDispatchLog.DEFAULT_BACKOFF_MS,
                    "{\"channel\":\"MOBILE_PUSH\",\"deviceToken\":\"" + sub.getDeviceToken() + "\"}");
            dispatchLogIds.add(mobileLogId);

            boolean mobileOk = mobilePushAdapter.push(sub.getDeviceToken(), title, content,
                    message.getBizType(), message.getBizId());
            if (mobileOk) {
                markDispatchSent(mobileLogId);
                mobilePushTriggered = true;
            } else {
                markDispatchFailed(mobileLogId, "Mock mobile push returned false");
            }
        }

        // 8. 返回结果
        SendNotificationVO vo = new SendNotificationVO();
        vo.setMessageId(message.getId());
        vo.setTitle(title);
        vo.setContent(content);
        vo.setMsgType(template.getMsgType());
        vo.setReceiverId(request.getReceiverId());
        vo.setCreatedTime(message.getCreatedTime());
        vo.setDispatchLogIds(dispatchLogIds);
        vo.setRealtimePushed(realtimePushed);
        vo.setMobilePushTriggered(mobilePushTriggered);
        return vo;
    }

    // ==================== 核心能力 2: 通知统计 ====================

    public NotificationStatsVO getStats() {
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        OffsetDateTime todayStart = OffsetDateTime.now().withHour(0).withMinute(0).withSecond(0).withNano(0);

        NotificationStatsVO stats = new NotificationStatsVO();
        // 模板数
        stats.setTemplateCount(templateMapper.selectCount(new LambdaQueryWrapper<SysMessageTemplate>()
                .eq(SysMessageTemplate::getTenantId, tenantId)));
        stats.setEnabledTemplateCount(templateMapper.selectCount(new LambdaQueryWrapper<SysMessageTemplate>()
                .eq(SysMessageTemplate::getTenantId, tenantId)
                .eq(SysMessageTemplate::getStatus, SysMessageTemplate.STATUS_ENABLED)));
        // 今日站内信
        stats.setTodayMessageCount(messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, tenantId)
                .ge(SysMessage::getCreatedTime, todayStart)));
        stats.setTodayReadCount(messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, tenantId)
                .ge(SysMessage::getReadTime, todayStart)));
        stats.setTodayUnreadCount(messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, tenantId)
                .ge(SysMessage::getCreatedTime, todayStart)
                .eq(SysMessage::getReadStatus, "UNREAD")));
        // 今日分发日志
        stats.setTodayDispatchCount(dispatchLogMapper.selectCount(new LambdaQueryWrapper<SysNotificationDispatchLog>()
                .eq(SysNotificationDispatchLog::getTenantId, tenantId)
                .ge(SysNotificationDispatchLog::getCreatedTime, todayStart)));
        stats.setTodayFailedDispatchCount(dispatchLogMapper.selectCount(new LambdaQueryWrapper<SysNotificationDispatchLog>()
                .eq(SysNotificationDispatchLog::getTenantId, tenantId)
                .ge(SysNotificationDispatchLog::getCreatedTime, todayStart)
                .in(SysNotificationDispatchLog::getDispatchStatus,
                        SysNotificationDispatchLog.STATUS_FAILED,
                        SysNotificationDispatchLog.STATUS_RETRYING)));
        // 待重试 + 死信
        stats.setPendingRetryCount(dispatchLogMapper.selectCount(new LambdaQueryWrapper<SysNotificationDispatchLog>()
                .eq(SysNotificationDispatchLog::getTenantId, tenantId)
                .in(SysNotificationDispatchLog::getDispatchStatus,
                        SysNotificationDispatchLog.STATUS_FAILED,
                        SysNotificationDispatchLog.STATUS_RETRYING)));
        stats.setDeadLetterCount(dispatchLogMapper.selectCount(new LambdaQueryWrapper<SysNotificationDispatchLog>()
                .eq(SysNotificationDispatchLog::getTenantId, tenantId)
                .eq(SysNotificationDispatchLog::getDispatchStatus, SysNotificationDispatchLog.STATUS_DEAD_LETTER)));
        // 当前用户未读数
        Long unread = messageMapper.selectCount(new LambdaQueryWrapper<SysMessage>()
                .eq(SysMessage::getTenantId, tenantId)
                .eq(SysMessage::getReceiverId, userId)
                .eq(SysMessage::getReadStatus, "UNREAD"));
        stats.setCurrentUserUnreadCount(unread != null ? unread : 0L);
        // 订阅数
        stats.setSubscriptionCount(subscriptionMapper.selectCount(new LambdaQueryWrapper<SysNotificationSubscription>()
                .eq(SysNotificationSubscription::getTenantId, tenantId)
                .eq(SysNotificationSubscription::getUserId, userId)
                .eq(SysNotificationSubscription::getEnabled, true)));
        return stats;
    }

    // ==================== 模板管理 ====================

    public PageResult<SysMessageTemplate> pageTemplates(int pageNo, int pageSize, String msgType) {
        DataScope scope = dataScopeResolver.resolve(RESOURCE_CODE);
        LambdaQueryWrapper<SysMessageTemplate> wrapper = new LambdaQueryWrapper<SysMessageTemplate>()
                .eq(SysMessageTemplate::getTenantId, CurrentUserContext.getTenantId())
                .eq(msgType != null && !msgType.isBlank(), SysMessageTemplate::getMsgType, msgType)
                .orderByDesc(SysMessageTemplate::getCreatedTime);
        applyDataScope(wrapper, scope);
        Page<SysMessageTemplate> page = templateMapper.selectPage(new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    /**
     * GA2-DS: 对 LambdaQueryWrapper 追加 DataScope 过滤条件。
     * SysMessageTemplate 实体无 owner_user_id 字段，使用 created_by (BaseEntity) 作为 owner 字段。
     * - ALL/TENANT: 无附加条件 (admin/viewer)
     * - SELF/DEPT/DEPT_AND_CHILD/CUSTOM/NONE: 安全降级为 created_by = currentUserId
     */
    private void applyDataScope(LambdaQueryWrapper<SysMessageTemplate> wrapper, DataScope scope) {
        if (scope == null) {
            return;
        }
        if (scope.scopeType() == DataScopeType.ALL || scope.scopeType() == DataScopeType.TENANT) {
            return;
        }
        String userId = scope.userId();
        if (userId == null || userId.isBlank()) {
            wrapper.apply("1 = 0");
            return;
        }
        wrapper.eq(SysMessageTemplate::getCreatedBy, userId);
    }

    @Transactional
    public SysMessageTemplate createTemplate(SaveTemplateRequest request) {
        String tenantId = CurrentUserContext.getTenantId();
        // 唯一性校验
        Long exists = templateMapper.selectCount(new LambdaQueryWrapper<SysMessageTemplate>()
                .eq(SysMessageTemplate::getTenantId, tenantId)
                .eq(SysMessageTemplate::getTemplateCode, request.getTemplateCode()));
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.NOT_REQUEST_INVALID,
                    "模板代码已存在: " + request.getTemplateCode());
        }
        SysMessageTemplate template = new SysMessageTemplate();
        template.setId(IdGenerator.nextId());
        template.setTenantId(tenantId);
        template.setTemplateCode(request.getTemplateCode());
        template.setTemplateName(request.getTemplateName());
        // V002 NOT NULL 约束: channel 必填, 通知运营场景统一用 IN_APP
        template.setChannel(SysMessageTemplate.CHANNEL_IN_APP);
        template.setMsgType(request.getMsgType());
        template.setTitleTemplate(request.getTitleTemplate());
        template.setContentTemplate(request.getContentTemplate());
        template.setTargetRouteId(request.getTargetRouteId());
        template.setPriority(request.getPriority() != null ? request.getPriority() : SysMessageTemplate.PRIORITY_NORMAL);
        template.setDeliveryMode(request.getDeliveryMode() != null ? request.getDeliveryMode() : SysMessageTemplate.DELIVERY_PERSIST_THEN_PUSH);
        // V002 status 列: ENABLED/DISABLED (前端 enabled Boolean 转换)
        boolean enabled = request.getEnabled() == null || request.getEnabled();
        template.setStatus(enabled ? SysMessageTemplate.STATUS_ENABLED : SysMessageTemplate.STATUS_DISABLED);
        template.setRemark(request.getRemark());
        templateMapper.insert(template);
        return template;
    }

    @Transactional
    public void deleteTemplate(String id) {
        SysMessageTemplate template = templateMapper.selectById(id);
        if (template == null || Boolean.TRUE.equals(template.getDeleted())) {
            throw new ResourceNotFoundException(ErrorCode.NOT_TEMPLATE_NOT_FOUND, "模板不存在: " + id);
        }
        templateMapper.deleteById(id);
    }

    // ==================== 分发日志管理 ====================

    public PageResult<SysNotificationDispatchLog> pageDispatchLogs(DispatchLogPageQuery query) {
        LambdaQueryWrapper<SysNotificationDispatchLog> wrapper = new LambdaQueryWrapper<SysNotificationDispatchLog>()
                .eq(SysNotificationDispatchLog::getTenantId, CurrentUserContext.getTenantId())
                .eq(query.getDispatchStatus() != null && !query.getDispatchStatus().isBlank(),
                        SysNotificationDispatchLog::getDispatchStatus, query.getDispatchStatus())
                .eq(query.getChannel() != null && !query.getChannel().isBlank(),
                        SysNotificationDispatchLog::getChannel, query.getChannel())
                .eq(query.getReceiverId() != null && !query.getReceiverId().isBlank(),
                        SysNotificationDispatchLog::getReceiverId, query.getReceiverId())
                .orderByDesc(SysNotificationDispatchLog::getCreatedTime);
        Page<SysNotificationDispatchLog> page = dispatchLogMapper.selectPage(
                new Page<>(query.getPageNo(), query.getPageSize()), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), query.getPageNo(), query.getPageSize());
    }

    /**
     * 手动重试分发。将状态改为 RETRYING，retry_count+1，next_retry_time=now。
     * 若 retry_count >= max_retry_count，进入 DEAD_LETTER 终态。
     */
    @Transactional
    public SysNotificationDispatchLog retryDispatch(String id) {
        SysNotificationDispatchLog log = dispatchLogMapper.selectById(id);
        if (log == null || Boolean.TRUE.equals(log.getDeleted())) {
            throw new ResourceNotFoundException(ErrorCode.NOT_DISPATCH_NOT_FOUND, "分发日志不存在: " + id);
        }
        if (SysNotificationDispatchLog.STATUS_DEAD_LETTER.equals(log.getDispatchStatus())) {
            throw new BusinessException(ErrorCode.NOT_REQUEST_INVALID,
                    "分发已进入死信队列，不可重试: " + id);
        }
        if (SysNotificationDispatchLog.STATUS_SENT.equals(log.getDispatchStatus())) {
            throw new BusinessException(ErrorCode.NOT_REQUEST_INVALID,
                    "分发已成功，无需重试: " + id);
        }

        int newRetryCount = log.getRetryCount() + 1;
        log.setRetryCount(newRetryCount);

        // 尝试重新推送
        boolean ok = reDispatch(log);
        if (ok) {
            log.setDispatchStatus(SysNotificationDispatchLog.STATUS_SENT);
            log.setSentTime(OffsetDateTime.now());
            log.setNextRetryTime(null);
            log.setLastError(null);
        } else {
            // 判断是否进入死信
            if (newRetryCount >= log.getMaxRetryCount()) {
                log.setDispatchStatus(SysNotificationDispatchLog.STATUS_DEAD_LETTER);
                log.setNextRetryTime(null);
            } else {
                log.setDispatchStatus(SysNotificationDispatchLog.STATUS_RETRYING);
                long backoff = log.getRetryBackoffMs() * (1L << newRetryCount);
                log.setNextRetryTime(OffsetDateTime.now().plusNanos(backoff * 1_000_000L));
            }
        }
        dispatchLogMapper.updateById(log);
        return log;
    }

    /**
     * 手动解决死信。将状态改为 SENT（标记为人工解决）。
     */
    @Transactional
    public SysNotificationDispatchLog resolveDispatch(String id) {
        SysNotificationDispatchLog log = dispatchLogMapper.selectById(id);
        if (log == null || Boolean.TRUE.equals(log.getDeleted())) {
            throw new ResourceNotFoundException(ErrorCode.NOT_DISPATCH_NOT_FOUND, "分发日志不存在: " + id);
        }
        log.setDispatchStatus(SysNotificationDispatchLog.STATUS_SENT);
        log.setSentTime(OffsetDateTime.now());
        log.setNextRetryTime(null);
        dispatchLogMapper.updateById(log);
        return log;
    }

    /** 重试时根据 channel 重新分发 */
    private boolean reDispatch(SysNotificationDispatchLog log) {
        try {
            if (SysNotificationDispatchLog.CHANNEL_MOBILE_PUSH.equals(log.getChannel())) {
                // 从 payload_snapshot 提取 deviceToken
                String deviceToken = extractJsonField(log.getPayloadSnapshot(), "deviceToken");
                if (deviceToken == null) {
                    log.setLastError("payload_snapshot 缺少 deviceToken");
                    return false;
                }
                // 从 sys_message 提取 title/content
                SysMessage msg = messageMapper.selectById(log.getMessageId());
                if (msg == null) {
                    log.setLastError("关联 sys_message 不存在: " + log.getMessageId());
                    return false;
                }
                return mobilePushAdapter.push(deviceToken, msg.getTitle(), msg.getContent(),
                        msg.getBizType(), msg.getBizId());
            }
            // IN_APP / SMS / EMAIL 暂不实现真实分发，标记为成功
            return true;
        } catch (Exception e) {
            log.setLastError("reDispatch 异常: " + e.getMessage());
            return false;
        }
    }

    // ==================== 订阅管理 ====================

    public PageResult<SysNotificationSubscription> pageSubscriptions(int pageNo, int pageSize, String topic) {
        LambdaQueryWrapper<SysNotificationSubscription> wrapper = new LambdaQueryWrapper<SysNotificationSubscription>()
                .eq(SysNotificationSubscription::getTenantId, CurrentUserContext.getTenantId())
                .eq(SysNotificationSubscription::getUserId, CurrentUserContext.getUserId())
                .eq(topic != null && !topic.isBlank(), SysNotificationSubscription::getTopic, topic)
                .orderByDesc(SysNotificationSubscription::getCreatedTime);
        Page<SysNotificationSubscription> page = subscriptionMapper.selectPage(
                new Page<>(pageNo, pageSize), wrapper);
        return PageResult.of(page.getRecords(), page.getTotal(), pageNo, pageSize);
    }

    @Transactional
    public SysNotificationSubscription saveSubscription(SaveSubscriptionRequest request) {
        String tenantId = CurrentUserContext.getTenantId();
        String userId = CurrentUserContext.getUserId();
        // 查找已有订阅 (tenant_id + user_id + topic + channel 唯一)
        SysNotificationSubscription existing = subscriptionMapper.selectOne(new LambdaQueryWrapper<SysNotificationSubscription>()
                .eq(SysNotificationSubscription::getTenantId, tenantId)
                .eq(SysNotificationSubscription::getUserId, userId)
                .eq(SysNotificationSubscription::getTopic, request.getTopic())
                .eq(SysNotificationSubscription::getChannel, request.getChannel())
                .last("LIMIT 1"));
        if (existing != null) {
            // 更新
            existing.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
            if (request.getDeviceToken() != null) {
                existing.setDeviceToken(request.getDeviceToken());
            }
            if (request.getRemark() != null) {
                existing.setRemark(request.getRemark());
            }
            subscriptionMapper.updateById(existing);
            return existing;
        }
        // 新建
        SysNotificationSubscription sub = new SysNotificationSubscription();
        sub.setId(IdGenerator.nextId());
        sub.setTenantId(tenantId);
        sub.setUserId(userId);
        sub.setTopic(request.getTopic());
        sub.setChannel(request.getChannel());
        sub.setEnabled(request.getEnabled() != null ? request.getEnabled() : true);
        sub.setDeviceToken(request.getDeviceToken());
        sub.setRemark(request.getRemark());
        subscriptionMapper.insert(sub);
        return sub;
    }

    @Transactional
    public void deleteSubscription(String id) {
        SysNotificationSubscription sub = subscriptionMapper.selectById(id);
        if (sub == null || Boolean.TRUE.equals(sub.getDeleted())) {
            throw new ResourceNotFoundException(ErrorCode.NOT_SUBSCRIPTION_NOT_FOUND, "订阅不存在: " + id);
        }
        if (!CurrentUserContext.getUserId().equals(sub.getUserId())) {
            throw new BusinessException(ErrorCode.NOT_REQUEST_INVALID, "只能删除自己的订阅");
        }
        subscriptionMapper.deleteById(id);
    }

    // ==================== 内部工具方法 ====================

    /** 渲染模板变量: ${var} → variables.get(var) */
    private String renderTemplate(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        if (variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher matcher = VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = variables.getOrDefault(varName, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 创建分发日志 (PENDING) */
    private String createDispatchLog(String messageId, String receiverId, String channel,
                                      int maxRetry, long backoffMs, String payloadSnapshot) {
        SysNotificationDispatchLog log = new SysNotificationDispatchLog();
        log.setId(IdGenerator.nextId());
        log.setTenantId(CurrentUserContext.getTenantId());
        log.setMessageId(messageId);
        log.setReceiverId(receiverId);
        log.setChannel(channel);
        log.setDispatchStatus(SysNotificationDispatchLog.STATUS_PENDING);
        log.setRetryCount(0);
        log.setMaxRetryCount(maxRetry);
        log.setRetryBackoffMs(backoffMs);
        log.setPayloadSnapshot(payloadSnapshot);
        dispatchLogMapper.insert(log);
        return log.getId();
    }

    /** 标记分发为 SENT */
    private void markDispatchSent(String id) {
        SysNotificationDispatchLog update = new SysNotificationDispatchLog();
        update.setId(id);
        update.setDispatchStatus(SysNotificationDispatchLog.STATUS_SENT);
        update.setSentTime(OffsetDateTime.now());
        dispatchLogMapper.updateById(update);
    }

    /** 标记分发为 FAILED */
    private void markDispatchFailed(String id, String error) {
        LambdaUpdateWrapper<SysNotificationDispatchLog> wrapper = new LambdaUpdateWrapper<SysNotificationDispatchLog>()
                .eq(SysNotificationDispatchLog::getId, id)
                .set(SysNotificationDispatchLog::getDispatchStatus, SysNotificationDispatchLog.STATUS_FAILED)
                .set(SysNotificationDispatchLog::getLastError, truncate(error, 2048))
                .set(SysNotificationDispatchLog::getNextRetryTime, OffsetDateTime.now().plusSeconds(60));
        dispatchLogMapper.update(null, wrapper);
    }

    /** 从 JSON 字符串中提取字段值 (简单实现, 避免 Jackson 反序列化开销) */
    private String extractJsonField(String json, String fieldName) {
        if (json == null || json.isBlank()) {
            return null;
        }
        Pattern p = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
