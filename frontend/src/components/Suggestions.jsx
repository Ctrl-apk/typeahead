/**
 * Suggestions
 * ───────────
 * Dropdown list showing typeahead suggestions.
 * Handles loading skeleton, error state, and highlights the active item
 * during keyboard navigation.
 */
function Suggestions({ suggestions, activeIndex, loading, error, onSelect }) {

    if (loading) {
        return (
            <div className="suggestions-dropdown">
                <div className="loading-indicator">
                    <span className="spinner" aria-hidden="true" />
                    Loading suggestions…
                </div>
            </div>
        );
    }

    if (error) {
        return (
            <div className="suggestions-dropdown">
                <div className="loading-indicator" style={{ color: "#f87171" }}>
                    ⚠ {error}
                </div>
            </div>
        );
    }

    if (!suggestions.length) return null;

    return (
        <div className="suggestions-dropdown" role="listbox">
            <ul className="suggestions-list">
                {suggestions.map((item, idx) => (
                    <li
                        key={item.id}
                        role="option"
                        aria-selected={idx === activeIndex}
                        className={idx === activeIndex ? "active" : ""}
                        onClick={() => onSelect(item.query)}
                        onMouseDown={(e) => e.preventDefault()} /* keep input focus */
                    >
                        <span className="suggestion-icon" aria-hidden="true">⌕</span>
                        {item.query}
                        <span className="suggestion-count">{item.count?.toLocaleString()}</span>
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default Suggestions;
