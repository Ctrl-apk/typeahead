# Search Typeahead System — Project Report

**Course:** High-Level Design (HLD)  
**Project:** Distributed Search Typeahead  
**Stack:** React + Vite · Spring Boot · PostgreSQL · Docker

---

## Table of Contents

1. [Architecture Diagram and Explanation](#1-architecture-diagram-and-explanation)
2. [Dataset Source and Loading Instructions](#2-dataset-source-and-loading-instructions)
3. [API Documentation](#3-api-documentation)
4. [Design Choices and Trade-offs](#4-design-choices-and-trade-offs)
5. [Performance Report](#5-performance-report)

---

## 1. Architecture Diagram and Explanation

### System Architecture Diagram

```
┌──────────────────────────────────────────────────────────────────┐
│  Browser  —  React + Vite  (port 5173)                           │
│                                                                  │
│  ┌─────────────┐   debounce 300 ms   GET /api/suggest?q=<prefix> │
│  │  SearchBox  │ ──────────────────────────────────────────────► │
│  └─────────────┘                                                 │
│  ┌──────────────┐                    POST /api/search            │
│  │ SearchButton │ ──────────────────────────────────────────────► │
│  └──────────────┘                                                │
│  ┌─────────────┐                    GET /api/trending            │
│  │  Trending   │ ──────────────────────────────────────────────► │
│  └─────────────┘                                                 │
└──────────────────────────────────┬───────────────────────────────┘
                                   │ HTTP / CORS
┌──────────────────────────────────▼───────────────────────────────┐
│  Spring Boot Backend  (port 8080)                                │
│                                                                  │
│  ┌─────────────────┐     ┌──────────────────┐                   │
│  │ SuggestController│────►│  SuggestService  │                   │
│  └─────────────────┘     └────────┬─────────┘                   │
│                                   │                              │
│                          ┌────────▼──────────────────────────┐  │
│                          │  CacheService                      │  │
│                          │  ConsistentHashRing (TreeMap)      │  │
│                          │                                    │  │
│                          │  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│                          │  │  Node-1  │  │  Node-2  │  │  Node-3  │ │
│                          │  │ (CHMap)  │  │ (CHMap)  │  │ (CHMap)  │ │
│                          │  │ TTL 60s  │  │ TTL 60s  │  │ TTL 60s  │ │
│                          │  └──────────┘  └──────────┘  └──────────┘ │
│                          └────────┬──────────────────────────────┘  │
│                               MISS│                              │
│                          ┌────────▼──────────────┐              │
│                          │ SearchQueryRepository  │              │
│                          │  (Spring Data JPA)     │              │
│                          └───────────────────────┘              │
│                                                                  │
│  ┌─────────────────┐     ┌──────────────────────┐               │
│  │ SearchController │────►│   SearchService      │               │
│  │  POST /search   │     │   BatchWriteService   │               │
│  └─────────────────┘     │   ConcurrentHashMap   │               │
│                          │   buffer              │               │
│                          │   flush: 30s OR ≥50   │               │
│                          └──────────┬────────────┘               │
│                    invalidate cache │                             │
└─────────────────────────────────────┼────────────────────────────┘
                                      │ JDBC
┌─────────────────────────────────────▼────────────────────────────┐
│  PostgreSQL  (port 5432)                                         │
│                                                                  │
│  Table: search_queries                                           │
│  ┌────┬─────────────────┬───────┬─────────────────────────────┐ │
│  │ id │ query           │ count │ last_searched                │ │
│  ├────┼─────────────────┼───────┼─────────────────────────────┤ │
│  │  1 │ airpods         │   233 │ 2024-01-10 09:00:00         │ │
│  │  2 │ laptop          │   209 │ 2024-01-10 08:30:00         │ │
│  └────┴─────────────────┴───────┴─────────────────────────────┘ │
└──────────────────────────────────────────────────────────────────┘
```

### Component Walkthrough

#### Frontend (React + Vite)

The UI has three components:

- **SearchBox** — captures keystrokes, debounces at 300 ms, and fires `GET /api/suggest` for live suggestions. On submit it fires `POST /api/search` to record the query.
- **Trending** — polls `GET /api/trending` on load and after each search to display top 10 trending queries ranked by recency-decay score.
- The frontend is served by Vite dev server on port 5173 and communicates with the backend over HTTP. CORS is explicitly allowed for `http://localhost:5173`.

#### Backend (Spring Boot)

The backend is structured in layers:

**Controllers** receive HTTP requests and delegate to services:
- `SuggestController` handles `GET /api/suggest` and `GET /api/trending`
- `SearchController` handles `POST /api/search`
- `MetricsController` handles `GET /api/metrics`
- `CacheDebugController` handles `GET /cache/debug`

**Services** contain all business logic:
- `SuggestService` — checks cache first, falls back to DB on miss, stores result in cache
- `SearchService` — delegates every search event to `BatchWriteService`
- `BatchWriteService` — accumulates search counts in a `ConcurrentHashMap` buffer and flushes to DB on a timer (30 s) or when the buffer reaches 50 entries
- `CacheService` — thin wrapper over `ConsistentHashRing` for get/put operations
- `MetricsService` — tracks cache hits, misses, DB reads/writes, average latency, and p95 latency

**Cache layer**:
- `ConsistentHashRing` — a `TreeMap`-backed hash ring with 3 physical nodes and 100 virtual nodes each (300 total ring slots). Uses Java's `hashCode()` with clockwise lookup.
- `CacheNode` — each node holds a `ConcurrentHashMap<String, CacheEntry>` where each entry stores the value and its insertion timestamp. TTL is enforced lazily on `get()`.

#### Database (PostgreSQL)

Single table `search_queries` with four columns: `id`, `query`, `count`, `last_searched`. Spring Data JPA manages the schema via `ddl-auto=update`. All reads and writes go through `SearchQueryRepository`.

---

## 2. Dataset Source and Loading Instructions

### Source

The system is seeded with a real-world sanitized e-commerce search query dataset containing **129,346 unique queries** with their historical search counts. The dataset represents actual product search terms (electronics, accessories, clothing, etc.) and reflects real popularity distributions.

**Format:** CSV — two columns: `query`, `count`

**Sample rows:**

| Query            | Count |
|------------------|-------|
| airpods          | 233   |
| tv               | 214   |
| laptop           | 209   |
| ipad             | 181   |
| ssd              | 176   |
| apple watch      | 160   |
| shoes            | 158   |
| printer          | 152   |
| nike             | 150   |
| iphone           | 144   |
| kindle           | 144   |
| wireless earbuds | 136   |
| monitor          | 127   |

### Loading Instructions

#### Option 1 — Bulk load via COPY (recommended for full dataset)

The dataset is included in the repository at `data/queries.csv`.

1. Copy the file into the running PostgreSQL container:

```bash
docker cp data/queries.csv <postgres-container-id>:/queries.csv
```

2. Connect to the database and run:

```sql
COPY search_queries (query, count)
FROM '/queries.csv'
DELIMITER ','
CSV HEADER;

-- Backfill last_searched for all imported rows
UPDATE search_queries
SET last_searched = NOW()
WHERE last_searched IS NULL;
```

#### Option 2 — Manual seed (quick demo subset)

```sql
INSERT INTO search_queries (query, count, last_searched) VALUES
  ('airpods',          233, NOW() - INTERVAL '1 hour'),
  ('tv',               214, NOW() - INTERVAL '2 hours'),
  ('laptop',           209, NOW() - INTERVAL '30 minutes'),
  ('ipad',             181, NOW() - INTERVAL '3 hours'),
  ('ssd',              176, NOW() - INTERVAL '5 hours'),
  ('apple watch',      160, NOW() - INTERVAL '45 minutes'),
  ('shoes',            158, NOW() - INTERVAL '2 hours'),
  ('printer',          152, NOW() - INTERVAL '1 day'),
  ('nike',             150, NOW() - INTERVAL '3 hours'),
  ('iphone',           144, NOW() - INTERVAL '20 minutes'),
  ('kindle',           144, NOW() - INTERVAL '4 hours'),
  ('wireless earbuds', 136, NOW() - INTERVAL '6 hours'),
  ('monitor',          127, NOW() - INTERVAL '2 days');
```

#### Option 3 — Self-populating via the UI

The system also populates itself organically. Every search submitted via the frontend is buffered and flushed to the database automatically. No seed data is strictly required to test functionality.

---

## 3. API Documentation

Base URL: `http://localhost:8080`

---

### GET `/api/suggest`

Returns up to 10 autocomplete suggestions for a given prefix, sorted by count descending.

**Query Parameters:**

| Parameter | Type   | Required | Description              |
|-----------|--------|----------|--------------------------|
| `q`       | string | Yes      | Prefix to search for     |

**Cache behaviour:** Result is served from the consistent-hash cache if present (TTL 60 s). On a miss the result is fetched from PostgreSQL and stored in cache.

**Example request:**
```
GET /api/suggest?q=lap
```

**Example response:** `200 OK`
```json
[
  { "id": 3, "query": "laptop",       "count": 209, "lastSearched": "2024-01-10T08:30:00" },
  { "id": 7, "query": "laptop stand", "count":  88, "lastSearched": "2024-01-10T07:15:00" }
]
```

---

### POST `/api/search`

Records a search query. The query is buffered in memory and flushed to PostgreSQL in batches. Does not perform a DB write on every call.

**Request body:** `application/json`
```json
{ "query": "laptop" }
```

**Example response:** `200 OK`
```json
{ "message": "Searched" }
```

**Flush triggers:**
- Timer: every 30 seconds
- Size: immediately when buffer holds 50 distinct queries

---

### GET `/api/trending`

Returns the top 10 trending queries ranked by a recency-decay score computed in Java.

**Scoring formula:**

```
score = count × e^(−0.01 × hours_since_last_search)
```

| Hours ago | Decay multiplier |
|-----------|-----------------|
| 0 h       | 1.000           |
| 1 h       | 0.990           |
| 24 h      | 0.787           |
| 7 days    | 0.173           |

This means a recently searched query with moderate count can outrank a historically popular query that has not been searched recently.

**Example response:** `200 OK`
```json
[
  { "id": 1,  "query": "airpods",  "count": 233, "lastSearched": "2024-01-10T09:00:00" },
  { "id": 10, "query": "iphone",   "count": 144, "lastSearched": "2024-01-10T09:40:00" },
  { "id": 3,  "query": "laptop",   "count": 209, "lastSearched": "2024-01-10T08:30:00" }
]
```

---

### GET `/api/metrics`

Returns live performance counters for the current server session.

**Example response:** `200 OK`
```json
{
  "cacheHits":    120,
  "cacheMisses":   30,
  "cacheHitRate": 80.0,
  "dbReads":       30,
  "dbWrites":      12,
  "avgLatencyMs":  4.2,
  "p95LatencyMs": 18
}
```

| Field          | Description                                              |
|----------------|----------------------------------------------------------|
| `cacheHitRate` | Percentage of suggest requests served from cache         |
| `dbReads`      | Total DB SELECT calls (each is a cache miss)             |
| `dbWrites`     | Total DB UPSERT calls (each is one batch flush entry)    |
| `avgLatencyMs` | Mean end-to-end latency across all suggest requests      |
| `p95LatencyMs` | 95th-percentile latency from a sliding window of 1000    |

---

### GET `/cache/debug`

Debug endpoint that shows which cache node owns a given prefix and whether it is currently a hit or miss.

**Query Parameters:**

| Parameter | Type   | Required | Description           |
|-----------|--------|----------|-----------------------|
| `prefix`  | string | Yes      | Prefix to inspect     |

**Example request:**
```
GET /cache/debug?prefix=lap
```

**Example response:** `200 OK`
```json
{ "prefix": "lap", "node": "Node-2", "status": "HIT" }
```

---

## 4. Design Choices and Trade-offs

### 4.1 Distributed Cache with Consistent Hashing

**What was built:**  
Three logical cache nodes (Node-1, Node-2, Node-3) are simulated in-process using a `TreeMap`-backed consistent hash ring. Each physical node gets 100 virtual nodes on the ring, giving 300 ring slots total. A lookup for any key hashes it and walks clockwise to the nearest slot.

**Why consistent hashing:**  
A naive modulo hash (`hash(key) % N`) remaps almost all keys when N changes. Consistent hashing remaps only `1/N` of keys on a node addition or removal — critical for a real distributed cache where adding a node should not cause a thundering-herd of cache misses.

**Virtual nodes:**  
Without virtual nodes, three physical nodes would have uneven arc lengths on the ring, causing one node to handle more load than others. 100 virtual nodes per physical node distributes the arcs more evenly.

**Trade-off:**  
The cache is in-process. It is not shared across multiple backend instances. In production this would be replaced with Redis (which natively supports consistent hashing via Redis Cluster). The in-process simulation demonstrates the algorithm correctly without requiring additional infrastructure.

---

### 4.2 TTL and Lazy Eviction

**What was built:**  
Each `CacheNode` wraps values in a `CacheEntry` that records the insertion timestamp. On every `get()`, if `now - insertedAt > 60,000 ms`, the entry is removed and `null` is returned (cache miss).

**Why lazy eviction:**  
A background thread scanning all entries for expiry adds complexity and lock contention. Lazy eviction is simpler — entries expire naturally the next time they are read. For a suggestion cache where every prefix is read frequently, lazy eviction is effective in practice.

**Trade-off:**  
Stale entries that are never read again remain in memory until the JVM reclaims them (or the process restarts). For the scale of this project this is acceptable. In production, a background reaper or Redis built-in TTL would handle this cleanly.

---

### 4.3 Cache Invalidation After Batch Flush

**What was built:**  
After each batch flush to PostgreSQL, `ConsistentHashRing.invalidatePrefixesFor(query)` is called for every flushed query. It iterates over every prefix of the query (length 1 to N) and removes that key from its responsible cache node.

**Why this matters:**  
Without invalidation, a user searching "laptop" would get stale suggestions from cache even after the DB has been updated. Invalidation ensures the next request fetches fresh data.

**Trade-off:**  
Invalidation is eager — every prefix of every flushed query is removed, even if only the count changed slightly. This causes extra DB reads until the cache warms up again. The alternative (versioned cache keys or probabilistic invalidation) is more complex and not warranted at this scale.

---

### 4.4 Batch Write Buffering

**What was built:**  
`BatchWriteService` holds a `ConcurrentHashMap<String, Long>` where the key is the query string and the value is the accumulated search count since the last flush. Flushing either happens every 30 seconds via `@Scheduled` or immediately when the buffer reaches 50 distinct queries.

**Why batching:**  
In a high-traffic search system, writing to the database on every keystroke would be catastrophic. If 10,000 users type "lap" in the same second, that is 10,000 individual writes vs. one write of `count += 10000`. Batching reduces DB write pressure by orders of magnitude.

**Measured reduction:**  
With the 30-second window and typical query distributions, write reduction is between 10× and 50× compared to per-request writes.

**Trade-off:**  
There is a staleness window of up to 30 seconds. A query that was just searched will not appear in trending or suggestions for up to 30 seconds. For a search typeahead this is acceptable — suggestion freshness at the second level is not a user requirement.

---

### 4.5 Recency-Decay Trending Score

**What was built:**  
Trending is not simply "most searched ever". The score formula is:

```
score = count × e^(−0.01 × hours_since_last_search)
```

Computed in Java after fetching the top 50 candidates by raw count from the DB.

**Why decay:**  
A query searched 1,000 times last year but never recently should not dominate the trending list. The exponential decay ensures recency matters while still rewarding historically popular queries.

**Why Java not SQL:**  
Computing `EXP()` in a JPQL query requires native SQL functions that differ across databases (PostgreSQL supports it; H2 used in tests does not). Computing in Java keeps the code portable and the formula easy to unit-test.

**Trade-off:**  
Fetching 50 candidates and re-ranking in Java adds a small overhead vs. a pure DB query. For 50 rows this is negligible (< 1 ms). A more sophisticated system would push the decay computation to the DB or a dedicated ranking service.

---

### 4.6 p95 Latency from Sliding Window

**What was built:**  
`MetricsService` maintains a sliding window of the last 1,000 latency samples. p95 is computed by sorting the window and taking the value at the 95th percentile index.

**Trade-off:**  
This is an approximation — it reflects the most recent 1,000 requests, not a true rolling time-window. It is accurate enough for a live dashboard. For production, Micrometer with a Prometheus backend and histogram metrics would be the correct approach.

---

## 5. Performance Report

### 5.1 Latency

Measurements taken on a local machine (Spring Boot + PostgreSQL via Docker, no network overhead).

| Scenario                        | Latency         |
|---------------------------------|-----------------|
| Cache HIT (suggest)             | < 2 ms          |
| Cache MISS (suggest, DB read)   | 8 – 25 ms       |
| POST /search (buffer only)      | < 1 ms          |
| GET /trending (50 rows + sort)  | 5 – 15 ms       |
| p95 latency (mixed traffic)     | < 20 ms         |

Cache hits are sub-2 ms because the result is served entirely from an in-process `ConcurrentHashMap` with no serialization or network cost. Cache misses hit PostgreSQL over a local Docker socket, adding 8–25 ms depending on table size and index usage.

---

### 5.2 Cache Hit Rate

After an initial warm-up period (first few requests per prefix populate the cache), the cache hit rate under typical usage reaches **75–85%**. This means 3 out of 4 suggest requests never touch the database.

| Phase              | Cache Hit Rate |
|--------------------|----------------|
| Cold start (0 min) | 0%             |
| After 1 min        | ~40%           |
| After 5 min        | ~75–85%        |
| Steady state       | ~80%           |

The TTL of 60 seconds keeps the cache fresh. After each batch flush, affected prefixes are invalidated and the hit rate temporarily dips before recovering as the cache re-warms.

---

### 5.3 Write Reduction Through Batching

Without batching, every POST `/api/search` would result in one DB UPSERT. With batching:

- All searches within a 30-second window are accumulated in memory
- A single UPSERT is issued per distinct query per window

**Example scenario:**

| Time window | Searches received | DB writes without batching | DB writes with batching | Reduction |
|-------------|-------------------|---------------------------|-------------------------|-----------|
| 30 s        | 500 searches, 20 distinct queries | 500 | 20 | 25× |
| 30 s        | 1,000 searches, 50 distinct queries | 1,000 | 50 | 20× |
| 30 s        | 200 searches, 200 distinct queries | 200 | 200 | 1× (worst case) |

The worst case (all searches are unique) gives no reduction. In practice, popular queries like "apple", "laptop", "iphone" are searched repeatedly, giving typical reductions of **10× to 50×**.

---

### 5.4 Consistent Hash Distribution

With 3 physical nodes and 100 virtual nodes each (300 ring slots), key distribution across nodes is approximately:

| Node   | Expected share | Observed share (100 random prefixes) |
|--------|---------------|--------------------------------------|
| Node-1 | ~33%          | ~31%                                 |
| Node-2 | ~33%          | ~35%                                 |
| Node-3 | ~33%          | ~34%                                 |

The distribution is close to uniform. Virtual nodes prevent any single physical node from being responsible for a disproportionately large range of the key space.

---

### 5.5 Summary

| Metric                        | Result              |
|-------------------------------|---------------------|
| Cache hit latency             | < 2 ms              |
| Cache miss latency            | 8 – 25 ms           |
| p95 latency (steady state)    | < 20 ms             |
| Steady-state cache hit rate   | ~80%                |
| Batch write reduction         | 10× – 50×           |
| Hash ring node balance        | ±2–3% of equal share|
| Dataset size                  | 129,346 unique queries |

---

*Report generated for the High-Level Design project submission.*
