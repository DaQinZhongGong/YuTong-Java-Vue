package com.yutong.system.realtime.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yutong.system.realtime.config.RealtimeProperties;
import com.yutong.system.realtime.dto.RealtimeEnvelope;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Service;

/**
 * 推送服务。设计来源: 44-实时通信与消息推送设计 line 22-31
 *
 * <p>职责:
 * <ul>
 *   <li>对内提供 push(envelope) 接口，业务事件触发推送</li>
 *   <li>本地有 session 则直接投递；同时发布到 Redis PubSub，由各实例的订阅者投递到本地 session</li>
 *   <li>realtime.push.enabled=false 时所有推送静默 no-op，业务不感知，自动回退到 REST 拉取</li>
 * </ul>
 *
 * <p>可靠性: 关键消息先写 sys_message 后推送（PERSIST_THEN_PUSH），即使本服务故障客户端也能通过 REST 拉取补偿。
 */
@Service
public class PushService {

    private static final Logger log = LoggerFactory.getLogger(PushService.class);

    private final RealtimeProperties properties;
    private final SessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    /** 可选依赖: 单实例部署时不强制要求 Redis（GA boot 模式默认无 Redis 也能跑）。 */
    private final StringRedisTemplate redisTemplate;

    public PushService(RealtimeProperties properties,
                       SessionRegistry sessionRegistry,
                       ObjectMapper objectMapper,
                       org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.properties = properties;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /**
     * 启动时订阅 Redis PubSub 频道。多实例时每个实例都订阅同一频道，收到消息后投递到本地 session。
     * 无 Redis（boot 单机模式）则跳过订阅，仅依赖本地 sessionRegistry 直投。
     */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            log.info("实时推送已禁用 (yutong.realtime.push.enabled=false)，端侧使用 REST 拉取");
            return;
        }
        if (redisTemplate == null) {
            log.info("Redis 未配置，PushService 单机模式（仅本地 session 推送）");
            return;
        }
        try {
            RedisMessageListenerContainer container = new RedisMessageListenerContainer();
            container.setConnectionFactory(redisTemplate.getConnectionFactory());
            MessageListenerAdapter listenerAdapter = new MessageListenerAdapter(this, "handleRedisMessage");
            // 必须显式调用 afterPropertiesSet() 以初始化 invoker 字段，
            // 否则 onMessage() 时 this.invoker 为 null 抛 NPE（Spring Data Redis 已知行为：构造器不初始化 invoker）
            listenerAdapter.afterPropertiesSet();
            container.addMessageListener(listenerAdapter, new ChannelTopic(properties.getRedisChannel()));
            container.afterPropertiesSet();
            container.start();
            log.info("PushService Redis PubSub 订阅启动 channel={}", properties.getRedisChannel());
        } catch (Exception e) {
            log.warn("PushService Redis PubSub 订阅失败，降级为本地单机模式 - {}", e.getMessage());
        }
    }

    /**
     * 业务侧调用入口。推送信封到 receiverUserId 关联的 channel。
     * 本地有 session 直投，同时发到 Redis PubSub 让其他实例投递。
     * realtime.push.enabled=false 时静默 no-op。
     */
    public void push(RealtimeEnvelope envelope) {
        if (!properties.isEnabled() || envelope == null || envelope.getReceiverUserId() == null) {
            return;
        }
        String channel = envelope.getChannel() != null ? envelope.getChannel()
                : "user:" + envelope.getReceiverUserId();
        // 1. 本地直投
        int localDelivered = sessionRegistry.sendToChannel(channel, envelope, objectMapper);
        // 2. 发布到 Redis PubSub（供其他实例投递到它们的本地 session）
        if (redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(envelope);
                redisTemplate.convertAndSend(properties.getRedisChannel(), json);
            } catch (Exception e) {
                log.warn("Redis PubSub 发布失败 messageId={} channel={} - {}",
                        envelope.getMessageId(), channel, e.getMessage());
            }
        }
        log.debug("推送 messageId={} channel={} localDelivered={} redisReplicated={}",
                envelope.getMessageId(), channel, localDelivered, redisTemplate != null);
    }

    /**
     * Redis PubSub 消息回调。本机收到其他实例发布的推送信封，投递到本地 session。
     * 注意：发布方自己也会收到这条消息（Redis PubSub 不区分发布者），但本地 session 已在 push() 中直投过，
     * 此处重复投递会引发客户端重复展示，因此需用 messageId 去重。
     * 简化实现: 本方法仅处理本地 channel 投递，重复投递由客户端 ACK_DELIVERED 幂等去重保护（44 号文档 line 96/124）。
     */
    public void handleRedisMessage(String message, String pattern) {
        try {
            RealtimeEnvelope envelope = objectMapper.readValue(message, RealtimeEnvelope.class);
            sessionRegistry.sendToChannel(envelope.getChannel(), envelope, objectMapper);
        } catch (Exception e) {
            log.warn("Redis PubSub 消息处理失败 - {}", e.getMessage());
        }
    }
}
