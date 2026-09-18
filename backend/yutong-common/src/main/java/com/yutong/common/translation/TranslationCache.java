package com.yutong.common.translation;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 翻译缓存 — 5 分钟 TTL, 防止同一请求内重复查表
 * 设计来源: platform-common-translation + ADR 0004 P2-A
 *
 * 落点:84-字典/用户/部门显示名自动化详设
 *
 * 简化版 ConcurrentHashMap + Instant TTL, 不引 Caffeine 避免多一个依赖
 * 容量限制 10000, 超出后清空一半 (LRU-like 简化)
 */
public class TranslationCache {

    /** 默认 TTL 5 分钟 */
    public static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    /** 容量上限, 超出触发清理 */
    public static final int DEFAULT_MAX_SIZE = 10_000;

    private final long ttlMillis;
    private final int maxSize;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public TranslationCache() {
        this(DEFAULT_TTL, DEFAULT_MAX_SIZE);
    }

    public TranslationCache(Duration ttl, int maxSize) {
        this.ttlMillis = ttl.toMillis();
        this.maxSize = maxSize;
    }

    public String get(String key) {
        if (key == null) return null;
        CacheEntry e = cache.get(key);
        if (e == null) return null;
        if (e.isExpired()) {
            cache.remove(key);
            return null;
        }
        return e.value;
    }

    public void put(String key, String value) {
        if (key == null) return;
        if (cache.size() >= maxSize) {
            evictHalf();
        }
        cache.put(key, new CacheEntry(value, System.currentTimeMillis() + ttlMillis));
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private void evictHalf() {
        // 简化 LRU: 清理过期项优先, 仍超则随机清一半
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(en -> en.getValue().expiresAt <= now);
        if (cache.size() >= maxSize) {
            int toRemove = cache.size() / 2;
            int removed = 0;
            for (String k : cache.keySet()) {
                if (removed >= toRemove) break;
                cache.remove(k);
                removed++;
            }
        }
    }

    private static final class CacheEntry {
        final String value;
        final long expiresAt;

        CacheEntry(String value, long expiresAt) {
            this.value = value;
            this.expiresAt = expiresAt;
        }

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
