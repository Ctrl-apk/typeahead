package com.shifa.typeahead.service;

import com.shifa.typeahead.cache.ConsistentHashRing;
import com.shifa.typeahead.model.SearchQuery;
import com.shifa.typeahead.repository.SearchQueryRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Buffers search events in-memory and flushes to PostgreSQL either:
 *  - every 30 seconds (timer-based), OR
 *  - immediately when the buffer reaches BATCH_SIZE_THRESHOLD entries.
 *
 * After each flush, the consistent-hash cache is invalidated for affected
 * prefixes so stale suggestion results are not served.
 */
@Service
public class BatchWriteService {

    /** Flush immediately when this many distinct queries are buffered */
    private static final int BATCH_SIZE_THRESHOLD = 50;

    private final SearchQueryRepository repository;
    private final MetricsService metricsService;
    private final ConsistentHashRing ring;

    private final ConcurrentHashMap<String, Long> buffer =
            new ConcurrentHashMap<>();

    public BatchWriteService(
            SearchQueryRepository repository,
            MetricsService metricsService,
            ConsistentHashRing ring
    ) {
        this.repository = repository;
        this.metricsService = metricsService;
        this.ring = ring;
    }

    public void recordSearch(String query) {
        buffer.merge(query, 1L, Long::sum);

        // Size-based flush trigger
        if (buffer.size() >= BATCH_SIZE_THRESHOLD) {
            System.out.println("[BatchWriteService] Size threshold reached ("
                    + BATCH_SIZE_THRESHOLD + "), flushing early.");
            flush();
        }
    }

    @Scheduled(fixedRate = 30_000)
    public synchronized void flush() {
        if (buffer.isEmpty()) {
            return;
        }

        System.out.println("[BatchWriteService] Flushing batch, size="
                + buffer.size());

        buffer.forEach((query, count) -> {
            try {

                System.out.println("Processing: " + query);

                var existing = repository.findByQuery(query);

                if (existing.isPresent()) {

                    System.out.println("Updating existing query");

                    SearchQuery q = existing.get();
                    q.setCount(q.getCount() + count);
                    q.setLastSearched(LocalDateTime.now());

                    metricsService.incrementDbWrite();
                    repository.save(q);

                    System.out.println("Updated successfully");

                } else {

                    System.out.println("Creating new query");

                    SearchQuery q = new SearchQuery();
                    q.setQuery(query);
                    q.setCount(count);
                    q.setLastSearched(LocalDateTime.now());

                    metricsService.incrementDbWrite();
                    repository.save(q);

                    System.out.println("Inserted successfully");
                }

                ring.invalidatePrefixesFor(query);

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        int flushed = buffer.size();
        buffer.clear();
        System.out.println("[BatchWriteService] Flushed " + flushed
                + " entries. Cache prefixes invalidated.");
    }
}