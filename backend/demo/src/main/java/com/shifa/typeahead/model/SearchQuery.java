package com.shifa.typeahead.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "search_queries")
public class SearchQuery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String query;

    private Long count;

    @Column(name = "last_searched")
    private LocalDateTime lastSearched;

    public SearchQuery() {
    }

    public Long getId() {
        return id;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Long getCount() {
        return count;
    }

    public void setCount(Long count) {
        this.count = count;
    }

    public LocalDateTime getLastSearched() {
        return lastSearched;
    }

    public void setLastSearched(LocalDateTime lastSearched) {
        this.lastSearched = lastSearched;
    }
}