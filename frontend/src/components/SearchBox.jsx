import { useState, useEffect, useRef, useCallback } from "react";
import axios from "axios";
import Suggestions from "./Suggestions";

const API = "http://localhost:8080/api";

/**
 * SearchBox
 * ─────────
 * Features:
 *  - Debounced suggest fetch (300 ms)
 *  - Keyboard navigation: ArrowUp / ArrowDown / Enter / Escape
 *  - Loading state while fetching suggestions
 *  - Error state shown in-UI (not just console.log)
 *  - Search button submits via POST /search
 */
function SearchBox({ onSearch }) {

    const [query, setQuery]           = useState("");
    const [suggestions, setSuggestions] = useState([]);
    const [activeIndex, setActiveIndex] = useState(-1);
    const [loading, setLoading]       = useState(false);
    const [fetchError, setFetchError] = useState(null);
    const [statusMsg, setStatusMsg]   = useState({ text: "", type: "" });
    const [searching, setSearching]   = useState(false);

    const inputRef = useRef(null);

    // ── Listen for trending chip selections from parent ────────────────
    useEffect(() => {
        const handler = (e) => {
            setQuery(e.detail);
            setSuggestions([]);
            handleSearch(e.detail);
        };
        window.addEventListener("typeahead:select", handler);
        return () => window.removeEventListener("typeahead:select", handler);
    // handleSearch is stable via useCallback, safe dep
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // ── Debounced suggest fetch ────────────────────────────────────────
    useEffect(() => {
        if (query.trim() === "") {
            setSuggestions([]);
            setActiveIndex(-1);
            setFetchError(null);
            return;
        }

        setLoading(true);
        setFetchError(null);

        const timer = setTimeout(() => {
            axios
                .get(`${API}/suggest?q=${encodeURIComponent(query)}`)
                .then((res) => {
                    setSuggestions(res.data);
                    setActiveIndex(-1);
                })
                .catch(() => {
                    setFetchError("Could not load suggestions. Is the server running?");
                    setSuggestions([]);
                })
                .finally(() => setLoading(false));
        }, 300);

        return () => {
            clearTimeout(timer);
            setLoading(false);
        };
    }, [query]);

    // ── POST /search ───────────────────────────────────────────────────
    const handleSearch = useCallback(async (searchQuery) => {
        const q = (searchQuery ?? query).trim();
        if (!q) return;

        setSearching(true);
        setSuggestions([]);
        setActiveIndex(-1);

        try {
            const res = await axios.post(`${API}/search`, { query: q });
            setStatusMsg({ text: res.data.message, type: "success" });
            onSearch?.();            // notify parent to refresh trending
        } catch {
            setStatusMsg({ text: "Search failed. Try again.", type: "error" });
        } finally {
            setSearching(false);
        }
    }, [query, onSearch]);

    // ── Keyboard navigation ────────────────────────────────────────────
    const handleKeyDown = (e) => {
        if (!suggestions.length) return;

        if (e.key === "ArrowDown") {
            e.preventDefault();
            setActiveIndex((i) => Math.min(i + 1, suggestions.length - 1));
        } else if (e.key === "ArrowUp") {
            e.preventDefault();
            setActiveIndex((i) => Math.max(i - 1, -1));
        } else if (e.key === "Enter") {
            e.preventDefault();
            if (activeIndex >= 0) {
                const selected = suggestions[activeIndex].query;
                setQuery(selected);
                handleSearch(selected);
            } else {
                handleSearch(query);
            }
        } else if (e.key === "Escape") {
            setSuggestions([]);
            setActiveIndex(-1);
        }
    };

    const handleSelect = (selected) => {
        setQuery(selected);
        handleSearch(selected);
        inputRef.current?.focus();
    };

    const showDropdown = (loading || suggestions.length > 0 || fetchError) && query.trim() !== "";

    return (
        <div className="search-wrapper">
            <div className="search-row">
                <input
                    ref={inputRef}
                    className={`search-input${fetchError ? " error" : ""}`}
                    type="text"
                    placeholder="Search anything…"
                    value={query}
                    onChange={(e) => {
                        setQuery(e.target.value);
                        setStatusMsg({ text: "", type: "" });
                    }}
                    onKeyDown={handleKeyDown}
                    autoComplete="off"
                    aria-label="Search input"
                    aria-autocomplete="list"
                    aria-expanded={showDropdown}
                />
                <button
                    className="search-btn"
                    onClick={() => handleSearch(query)}
                    disabled={searching || !query.trim()}
                    aria-label="Submit search"
                >
                    {searching ? "…" : "Search"}
                </button>
            </div>

            {/* Status message (success / error from POST) */}
            {statusMsg.text && (
                <p className={`status-message ${statusMsg.type}`}>
                    {statusMsg.text}
                </p>
            )}

            {/* Suggestions dropdown */}
            {showDropdown && (
                <Suggestions
                    suggestions={suggestions}
                    activeIndex={activeIndex}
                    loading={loading}
                    error={fetchError}
                    onSelect={handleSelect}
                />
            )}
        </div>
    );
}

export default SearchBox;
