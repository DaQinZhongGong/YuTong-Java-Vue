package com.yutong.system.realtime.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SseEmitterManager 单元测试。
 * 覆盖:
 *  - register + countTopic
 *  - sendToTopic 无连接不抛错
 *  - completeTopic 清理
 *  - stats 返回正确
 *  - 多 topic 隔离
 */
class SseEmitterManagerTest {

    private SseEmitterManager manager;

    @BeforeEach
    void setUp() {
        manager = new SseEmitterManager();
    }

    @Test
    void register_incrementsCount() {
        manager.register("user:1");
        manager.register("user:1");
        manager.register("user:2");
        assertEquals(2, manager.countTopic("user:1"));
        assertEquals(1, manager.countTopic("user:2"));
    }

    @Test
    void sendToTopic_noConnection_noThrow() {
        assertDoesNotThrow(() -> manager.sendToTopic("nonexistent", "delta", "hello"));
    }

    @Test
    void completeTopic_clearsConnections() {
        manager.register("user:1");
        manager.register("user:1");
        manager.completeTopic("user:1", Map.of("status", "done"));
        assertEquals(0, manager.countTopic("user:1"));
    }

    @Test
    void stats_returnsTopics() {
        manager.register("user:1");
        manager.register("user:2");
        manager.register("user:2");
        Map<String, Integer> stats = manager.stats();
        assertEquals(2, stats.size());
        assertEquals(1, stats.get("user:1"));
        assertEquals(2, stats.get("user:2"));
    }

    @Test
    void topics_areIsolated() {
        manager.register("conv:a");
        manager.register("conv:b");
        manager.completeTopic("conv:a", null);
        assertEquals(0, manager.countTopic("conv:a"));
        assertEquals(1, manager.countTopic("conv:b"));
    }

    @Test
    void register_returnsEmitter() {
        SseEmitter emitter = manager.register("user:1", 1000);
        assertNotNull(emitter);
    }
}
