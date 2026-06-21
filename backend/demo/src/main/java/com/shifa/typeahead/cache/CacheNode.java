package com.shifa.typeahead.cache;

import java.util.concurrent.ConcurrentHashMap;

/**
 * A single logical cache node in the consistent hash ring.
 * Supports TTL-based expiry: entries older than TTL_MS are evicted on read.
 */
public class CacheNode {

    private static final long TTL_MS = 60_000; // 1 minute TTL

    private final String nodeName;

    // Stores the cached value alongside its insertion timestamp
    private final ConcurrentHashMap<String, CacheEntry> cache =
            new ConcurrentHashMap<>();

    public CacheNode(String nodeName) {
        this.nodeName = nodeName;
    }

    public String getNodeName() {
        return nodeName;
    }

    /**
     * Returns the cached value if present and not expired, otherwise null.
     */
    public Object get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() - entry.insertedAt > TTL_MS) {
            cache.remove(key); // lazy eviction
            System.out.println("[" + nodeName + "] TTL expired for key: " + key);
            return null;
        }
        return entry.value;
    }

    public void put(String key, Object value) {
        cache.put(key, new CacheEntry(value, System.currentTimeMillis()));
    }

    /** Explicitly invalidate a key (called after a write flushes to DB). */
    public void invalidate(String key) {
        cache.remove(key);
    }

    /** Invalidate all keys that start with prefix (used after batch flush). */
    public void invalidateByPrefix(String prefix) {
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    // ── Internal entry wrapper ────────────────────────────────────────────────

    private static class CacheEntry {
        final Object value;
        final long insertedAt;

        CacheEntry(Object value, long insertedAt) {
            this.value = value;
            this.insertedAt = insertedAt;
        }
    }
}