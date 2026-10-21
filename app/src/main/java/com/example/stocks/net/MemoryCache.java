package com.example.stocks.net;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 60 秒内存缓存（LRU）。
 * 命中缓存时不再发起网络请求，既提速也降低被网站封禁的风险。
 *
 * @param <V> 缓存值类型
 */
public class MemoryCache<V> {

    /** 默认存活时间：60 秒 */
    public static final long DEFAULT_TTL_MS = 60_000L;

    private static class Entry<V> {
        final V value;
        final long createdAt;

        Entry(V value, long createdAt) {
            this.value = value;
            this.createdAt = createdAt;
        }
    }

    private final long ttlMs;
    private final int maxSize;
    private final LinkedHashMap<String, Entry<V>> map =
            new LinkedHashMap<String, Entry<V>>(16, 0.75f, true); // accessOrder=true 便于 LRU 淘汰

    public MemoryCache(long ttlMs, int maxSize) {
        this.ttlMs = ttlMs;
        this.maxSize = maxSize;
    }

    /** 读取缓存：过期或不存在返回 null */
    public synchronized V get(String key) {
        Entry<V> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.createdAt > ttlMs) {
            map.remove(key); // 过期即删除
            return null;
        }
        return entry.value;
    }

    /** 写入缓存，并按 LRU 策略淘汰最久未使用的条目 */
    public synchronized void put(String key, V value) {
        if (key == null || value == null) {
            return;
        }
        map.put(key, new Entry<V>(value, System.currentTimeMillis()));
        while (map.size() > maxSize) {
            Map.Entry<String, Entry<V>> eldest = map.entrySet().iterator().next();
            map.remove(eldest.getKey());
        }
    }

    /** 清空缓存（例如用户强制刷新全部分类时可选调用） */
    public synchronized void clear() {
        map.clear();
    }
}
