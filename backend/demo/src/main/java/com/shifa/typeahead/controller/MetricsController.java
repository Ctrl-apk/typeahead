package com.shifa.typeahead.controller;

import com.shifa.typeahead.service.MetricsService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(
            MetricsService metricsService
    ) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {

        Map<String, Object> data =
                new HashMap<>();

        data.put(
                "cacheHits",
                metricsService.getCacheHits()
        );

        data.put(
                "cacheMisses",
                metricsService.getCacheMisses()
        );

        data.put(
                "cacheHitRate",
                metricsService.getHitRate()
        );

        data.put(
                "dbReads",
                metricsService.getDbReads()
        );

        data.put(
                "dbWrites",
                metricsService.getDbWrites()
        );

        data.put(
                "avgLatencyMs",
                metricsService.getAverageLatency()
        );

        data.put(
                "p95LatencyMs",
                metricsService.getP95Latency()
        );

        return data;
    }
}