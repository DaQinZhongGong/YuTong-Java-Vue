package com.yutong.system.realtime.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * SSE 跨实例广播 — Redis Pub/Sub 监听器。
 * 落点: 业界同类实现 SseMessageUtils + ADR 0005 P1-D。
 *
 * <p>设计:
 * <ul>
 *   <li>频道: yutong:sse:broadcast</li>
 *   <li>消息格式: {"topic":"user:123","event":"delta","data":"..."}</li>
 *   <li>收到消息后转发给本地 SseEmitterManager 对应 topic 的所有连接</li>
 *   <li>无 Redis 时此 Bean 不注册 (降级为仅本地广播)</li>
 * </ul>
 */
@Component
public class SseBroadcastListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(SseBroadcastListener.class);

    public static final String CHANNEL = "yutong:sse:broadcast";

    private final SseEmitterManager emitterManager;
    private final ObjectMapper objectMapper;

    public SseBroadcastListener(SseEmitterManager emitterManager, ObjectMapper objectMapper) {
        this.emitterManager = emitterManager;
        this.objectMapper = objectMapper;
    }

    /**
     * 注册到 Redis 监听容器 (由配置类调用)。
     */
    public void registerTo(RedisMessageListenerContainer container) {
        container.addMessageListener(this, new ChannelTopic(CHANNEL));
        log.info("[SseBroadcast] registered on channel={}", CHANNEL);
    }

    /**
     * 发布跨实例广播消息。
     */
    public void publish(StringRedisTemplate redis, String topic, String event, Object data) {
        try {
            Map<String, Object> msg = Map.of("topic", topic, "event", event, "data", data);
            redis.convertAndSend(CHANNEL, objectMapper.writeValueAsString(msg));
        } catch (Exception e) {
            log.warn("[SseBroadcast] publish failed topic={}, error={}", topic, e.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            Map<?, ?> msg = objectMapper.readValue(json, Map.class);
            String topic = (String) msg.get("topic");
            String event = (String) msg.get("event");
            Object data = msg.get("data");
            if (topic != null && event != null) {
                emitterManager.sendToTopic(topic, event, data);
            }
        } catch (Exception e) {
            log.warn("[SseBroadcast] onMessage parse failed: {}", e.getMessage());
        }
    }
}
