package com.shifa.typeahead.repository;

import com.shifa.typeahead.model.SearchQuery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SearchQueryRepository
        extends JpaRepository<SearchQuery, Long> {

    Optional<SearchQuery> findByQuery(String query);

    @Query("""
            SELECT s
            FROM SearchQuery s
            WHERE LOWER(s.query)
            LIKE LOWER(CONCAT(:prefix,'%'))
            ORDER BY s.count DESC
            """)
    List<SearchQuery> findSuggestions(
            @Param("prefix") String prefix,
            Pageable pageable
    );

    /**
     * Returns the top candidates for trending (by count), then Java applies
     * the recency-decay score so the query is dialect-neutral (H2 + PostgreSQL).
     * We fetch a wider pool (top 50 by count) so the decay re-ranking is meaningful.
     */
    @Query("""
            SELECT s
            FROM SearchQuery s
            ORDER BY s.count DESC
            """)
    List<SearchQuery> findTrendingCandidates(Pageable pageable);
}