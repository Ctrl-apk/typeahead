import { useState, useEffect } from "react";
import axios from "axios";

/**
 * Trending
 * ────────
 * Fetches /api/trending and renders a chip list.
 * Re-fetches whenever the `refreshKey` prop changes (e.g., after a search).
 */
function Trending({ onSelect, refreshKey }) {

    const [trending, setTrending]  = useState([]);
    const [loading, setLoading]    = useState(false);
    const [error, setError]        = useState(null);

    useEffect(() => {
        setLoading(true);
        axios
            .get("http://localhost:8080/api/trending")
            .then((res) => setTrending(res.data))
            .catch(() => setError("Could not load trending searches."))
            .finally(() => setLoading(false));
    }, [refreshKey]);

    if (error) {
        return (
            <div className="trending-section">
                <div className="trending-header">
                    <span className="icon">🔥</span> Trending
                </div>
                <p style={{ color: "#f87171", fontSize: "0.85rem" }}>{error}</p>
            </div>
        );
    }

    return (
        <div className="trending-section">
            <div className="trending-header">
                <span className="icon">🔥</span> Trending Searches
            </div>

            {loading ? (
                <div className="loading-indicator">
                    <span className="spinner" />
                    Loading…
                </div>
            ) : (
                <div className="trending-grid">
                    {trending.map((item, idx) => (
                        <button
                            key={item.id}
                            className="trending-chip"
                            onClick={() => onSelect(item.query)}
                            aria-label={`Search for ${item.query}`}
                        >
                            <span className="rank">#{idx + 1}</span>
                            {item.query}
                        </button>
                    ))}
                    {!trending.length && !loading && (
                        <p style={{ color: "#475569", fontSize: "0.85rem" }}>
                            No trending searches yet.
                        </p>
                    )}
                </div>
            )}
        </div>
    );
}

export default Trending;
