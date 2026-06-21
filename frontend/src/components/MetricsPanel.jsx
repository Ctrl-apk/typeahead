import { useState, useEffect, useCallback } from "react";
import axios from "axios";

/**
 * MetricsPanel
 * ────────────
 * Collapsible panel showing live performance metrics from /api/metrics.
 * Auto-refreshes every 10 seconds when open.
 */
function MetricsPanel() {

    const [open, setOpen]       = useState(false);
    const [metrics, setMetrics] = useState(null);
    const [loading, setLoading] = useState(false);

    const fetchMetrics = useCallback(() => {
        setLoading(true);
        axios
            .get("http://localhost:8080/api/metrics")
            .then((res) => setMetrics(res.data))
            .catch(() => {})
            .finally(() => setLoading(false));
    }, []);

    useEffect(() => {
        if (!open) return;
        fetchMetrics();
        const id = setInterval(fetchMetrics, 10_000);
        return () => clearInterval(id);
    }, [open, fetchMetrics]);

    const fmt = (v, decimals = 1) =>
        v == null ? "—" : typeof v === "number" ? v.toFixed(decimals) : v;

    const cards = metrics
        ? [
            { label: "Cache Hit Rate", value: `${fmt(metrics.cacheHitRate)}%` },
            { label: "Cache Hits",     value: metrics.cacheHits },
            { label: "Cache Misses",   value: metrics.cacheMisses },
            { label: "Avg Latency",    value: `${fmt(metrics.avgLatencyMs)} ms` },
            { label: "p95 Latency",    value: `${fmt(metrics.p95LatencyMs, 0)} ms` },
            { label: "DB Reads",       value: metrics.dbReads },
            { label: "DB Writes",      value: metrics.dbWrites },
        ]
        : [];

    return (
        <div className="metrics-panel">
            <div className="metrics-header" onClick={() => setOpen((o) => !o)}>
                <h3>📊 Performance Metrics</h3>
                <span className={`toggle-icon${open ? " open" : ""}`}>▾</span>
            </div>

            {open && (
                <>
                    {loading && !metrics ? (
                        <div className="loading-indicator" style={{ padding: "16px" }}>
                            <span className="spinner" /> Loading metrics…
                        </div>
                    ) : (
                        <div className="metrics-grid">
                            {cards.map((c) => (
                                <div key={c.label} className="metric-card">
                                    <div className="metric-value">{c.value}</div>
                                    <div className="metric-label">{c.label}</div>
                                </div>
                            ))}
                        </div>
                    )}
                    <div className="metrics-refresh">
                        <button onClick={fetchMetrics}>↻ Refresh</button>
                    </div>
                </>
            )}
        </div>
    );
}

export default MetricsPanel;
