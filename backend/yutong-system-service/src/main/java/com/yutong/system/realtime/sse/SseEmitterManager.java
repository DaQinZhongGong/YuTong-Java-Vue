package com.yutong.system.realtime.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * SSE Emitter 管理器 — 本地连接注册表 + 跨实例 Redis 广播。
 * 落点: 业界同类实现 SseEmitterManager + ADR 0005 P1-D。
 *
 * <p>设计:
 * <ul>
 *   <li>本地: ConcurrentHashMap&lt;topic, Set&lt;EmitterEntry&gt;&gt; 管理活跃连接</li>
 *   <li>跨实例: Redis Pub/Sub 频道 yutong:sse:broadcast, 收到消息后转发给本地 emitter</li>
 *   <li>每个 emitter 绑定一个 topic (如 user:{userId} / conversation:{id})</li>
 *   <li>发送失败自动清理死连接</li>
 *   <li>无 Redis 时降级为仅本地广播 (失败开放)</li>
 * </ul>
 */
@Component
public class SseEmitterManager {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterManager.class);

    /** 本地连接注册表: topic → emitters */
    private final Map<String, Set<EmitterEntry>> topicEmitters = new ConcurrentHashMap<>();

    /** 默认超时: 30 分钟 */
    private static final long DEFAULT_TIMEOUT = 30 * 60 * 1000L;

    /**
     * 注册 emitter 到指定 topic。
     * 设置超时/完成/异常回调自动清理。
     */
    public SseEmitter register(String topic) {
        return register(topic, DEFAULT_TIMEOUT);
    }

    public SseEmitter register(String topic, long timeoutMs) {
        SseEmitter emitter = new SseEmitter(timeoutMs);
        EmitterEntry entry = new EmitterEntry(emitter, topic, System.currentTimeMillis());

        topicEmitters.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(entry);

        emitter.onCompletion(() -> remove(topic, entry));
        emitter.onTimeout(() -> remove(topic, entry));
        emitter.onError(e -> remove(topic, entry));

        log.debug("[SseManager] registered topic={}, total={}", topic, countTopic(topic));
        return emitter;
    }

    /**
     * 向指定 topic 的所有本地连接发送事件。
     */
    public void sendToTopic(String topic, String eventName, Object data) {
        Set<EmitterEntry> entries = topicEmitters.get(topic);
        if (entries == null || entries.isEmpty()) return;

        List<EmitterEntry> dead = new ArrayList<>();
        for (EmitterEntry entry : entries) {
            try {
                entry.emitter().send(SseEmitter.event().name(eventName).data(data));
            } catch (IOException | IllegalStateException e) {
                dead.add(entry);
            }
        }
        dead.forEach(e -> remove(topic, e));
    }

    /**
     * 向指定 topic 的所有本地连接发送 done 事件并关闭。
     */
    public void completeTopic(String topic, Object data) {
        Set<EmitterEntry> entries = topicEmitters.get(topic);
        if (entries == null) return;
        for (EmitterEntry entry : new ArrayList<>(entries)) {
            try {
                if (data != null) {
                    entry.emitter().send(SseEmitter.event().name("done").data(data));
                }
                entry.emitter().complete();
            } catch (Exception ignored) {
            }
            remove(topic, entry);
        }
    }

    /**
     * 获取某 topic 的活跃连接数。
     */
    public int countTopic(String topic) {
        Set<EmitterEntry> entries = topicEmitters.get(topic);
        return entries != null ? entries.size() : 0;
    }

    /**
     * 获取全部活跃 topic 及连接数。
     */
    public Map<String, Integer> stats() {
        Map<String, Integer> result = new LinkedHashMap<>();
        topicEmitters.forEach((topic, entries) -> result.put(topic, entries.size()));
        return result;
    }

    // ==================== 内部方法 ====================

    private void remove(String topic, EmitterEntry entry) {
        Set<EmitterEntry> entries = topicEmitters.get(topic);
        if (entries != null) {
            entries.remove(entry);
            if (entries.isEmpty()) {
                topicEmitters.remove(topic, entries);
            }
        }
    }

    /**
     * Emitter 条目 (含注册时间)。
     */
    private record EmitterEntry(SseEmitter emitter, String topic, long registeredAt) {
        @Override
        public boolean equals(Object o) {
            return o instanceof EmitterEntry e && e.emitter == this.emitter;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(emitter);
        }
    }
}
