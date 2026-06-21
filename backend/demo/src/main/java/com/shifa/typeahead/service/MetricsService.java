package com.shifa.typeahead.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tracks runtime performance metrics:
 *  - Cache hit / miss counts and hit rate
 *  - DB read / write counts
 *  - Average latency and p95 latency (from a sliding window of last 1000 samples)
 */
@Service
public class MetricsService {

    private static final int LATENCY_WINDOW = 1000;

    private long cacheHits = 0;
    private long cacheMisses = 0;
    private long dbReads = 0;
    private long dbWrites = 0;

    private long totalLatencyMs = 0;
    private long requestCount = 0;

    /** Sliding window of recent latency samples for percentile computation */
    private final List<Long> latencySamples = new ArrayList<>();

    public synchronized void incrementCacheHit() { cacheHits++; }
    public synchronized void incrementCacheMiss() { cacheMisses++; }
    public synchronized void incrementDbRead() { dbReads++; }
    public synchronized void incrementDbWrite() { dbWrites++; }

    public synchronized void recordLatency(long latencyMs) {
        totalLatencyMs += latencyMs;
        requestCount++;
        latencySamples.add(latencyMs);
        if (latencySamples.size() > LATENCY_WINDOW) {
            latencySamples.remove(0);
        }
    }

    public long getCacheHits() { return cacheHits; }
    public long getCacheMisses() { return cacheMisses; }
    public long getDbReads() { return dbReads; }
    public long getDbWrites() { return dbWrites; }

    public double getHitRate() {
        long total = cacheHits + cacheMisses;
        return total == 0 ? 0 : (cacheHits * 100.0) / total;
    }

    public double getAverageLatency() {
        return requestCount == 0 ? 0 : (double) totalLatencyMs / requestCount;
    }

    /**
     * p95 latency: the latency below which 95% of requests fall.
     * Computed from the last LATENCY_WINDOW samples.
     */
    public synchronized long getP95Latency() {
        if (latencySamples.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(latencySamples);
        Collections.sort(sorted);
        int index = (int) Math.ceil(0.95 * sorted.size()) - 1;
        return sorted.get(Math.max(0, index));
    }
}