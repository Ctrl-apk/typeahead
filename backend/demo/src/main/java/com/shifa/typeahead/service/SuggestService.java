package com.shifa.typeahead.service;

import com.shifa.typeahead.model.SearchQuery;
import com.shifa.typeahead.repository.SearchQueryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class SuggestService {

    /**
     * Decay constant k in score = count × e^(−k × hours).
     * k = 0.01 → half-life ≈ 69 hours.
     *   - 1 h ago  → weight ≈ 0.99
     *   - 24 h ago → weight ≈ 0.79
     *   - 7 d ago  → weight ≈ 0.17
     */
    private static final double DECAY_K = 0.01;

    private final SearchQueryRepository repository;
    private final CacheService cacheService;
    private final MetricsService metricsService;

    public SuggestService(
            SearchQueryRepository repository,
            CacheService cacheService,
            MetricsService metricsService
    ) {
        this.repository = repository;
        this.cacheService = cacheService;
        this.metricsService = metricsService;
    }

    public List<SearchQuery> getSuggestions(String prefix) {

        long start = System.currentTimeMillis();

        List<SearchQuery> cached =
                (List<SearchQuery>) cacheService.get(prefix);

        if (cached != null) {
            metricsService.incrementCacheHit();
            metricsService.recordLatency(System.currentTimeMillis() - start);
            System.out.println("[SuggestService] CACHE HIT for prefix='" + prefix + "'");
            return cached;
        }

        metricsService.incrementCacheMiss();
        metricsService.incrementDbRead();
        System.out.println("[SuggestService] CACHE MISS for prefix='" + prefix + "'");

        List<SearchQuery> result =
                repository.findSuggestions(prefix, PageRequest.of(0, 10));

        cacheService.put(prefix, result);
        metricsService.recordLatency(System.currentTimeMillis() - start);

        return result;
    }

    /**
     * Returns top 10 trending queries using recency-decay scoring.
     *
     * Formula: score = count × e^(−DECAY_K × hours_since_last_search)
     *
     * Computed in Java (not SQL) so it works across all DB dialects (PostgreSQL,
     * H2 for tests, etc.) and the formula is easy to unit-test.
     */
    public List<SearchQuery> getTrending() {
        // Fetch top 50 by raw count as candidates, then re-rank by score
        List<SearchQuery> candidates =
                repository.findTrendingCandidates(PageRequest.of(0, 50));

        LocalDateTime now = LocalDateTime.now();

        return candidates.stream()
                .sorted(Comparator.comparingDouble(
                        (SearchQuery s) -> trendingScore(s, now)
                ).reversed())
                .limit(10)
                .toList();
    }

    private double trendingScore(SearchQuery s, LocalDateTime now) {
        if (s.getLastSearched() == null) return s.getCount();
        long hoursAgo = ChronoUnit.HOURS.between(s.getLastSearched(), now);
        return s.getCount() * Math.exp(-DECAY_K * hoursAgo);
    }
}