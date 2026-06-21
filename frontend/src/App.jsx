import { useState, useCallback } from "react";
import SearchBox from "./components/SearchBox";
import Trending from "./components/Trending";
import "./App.css";

function App() {

    // Incrementing this key tells <Trending> to re-fetch after a search
    const [trendingRefreshKey, setTrendingRefreshKey] = useState(0);

    const handleSearch = useCallback(() => {
        setTrendingRefreshKey((k) => k + 1);
    }, []);

    const handleTrendingSelect = useCallback((query) => {
        // Bubble the selection into SearchBox by lifting state isn't needed —
        // we just fire the trending chip click which calls handleSearch via the
        // shared POST flow through SearchBox's onSelect prop.
        // For simplicity we emit a custom event that SearchBox listens to.
        window.dispatchEvent(
            new CustomEvent("typeahead:select", { detail: query })
        );
    }, []);

    return (
        <div className="app">
            {/* ── Hero ─────────────────────────────────────────────── */}
            <div className="hero">
                <h1>Search Typeahead</h1>
                <p>Distributed cache · Consistent hashing · Batch writes</p>
            </div>

            {/* ── Search Box + Suggestions ──────────────────────────── */}
            <SearchBox onSearch={handleSearch} />

            {/* ── Trending Searches ─────────────────────────────────── */}
            <Trending
                refreshKey={trendingRefreshKey}
                onSelect={handleTrendingSelect}
            />
        </div>
    );
}

export default App;
