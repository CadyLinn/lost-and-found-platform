# Performance

Load-test results for the read API (browsing, search, nearby, item detail, match suggestions).

## Setup

| | |
|---|---|
| Machine | MacBook Air (Apple Silicon), everything on one laptop |
| Backend | Spring Boot 4.2, Java 25, `./gradlew bootRun`, default Hikari pool (10 connections) |
| Data | PostgreSQL 16 + Redis 7 in Docker; 2,000 seeded posts (`load-tests/seed.sql`) |
| Load | k6 0.54 in Docker, script `load-tests/browse.js` |
| Traffic mix | 30% list with filters · 25% keyword search · 10% nearby · 25% item detail · 10% matches |
| Shape | 20 s ramp-up → 60 s at peak → 10 s ramp-down |

Run it yourself:

```bash
docker exec -i infra-postgres-1 psql -U lostfound -d lostfound < load-tests/seed.sql
docker run --rm --add-host host.docker.internal:host-gateway \
  -v "$PWD/load-tests:/scripts" grafana/k6:0.54.0 run -e PEAK_VUS=100 -e THINK=0 /scripts/browse.js
```

Targets (k6 thresholds; the run fails if missed): error rate < 1%, p95 < 300 ms for list, search and nearby,
< 200 ms for detail, < 500 ms for matches.

## Results

### 1. Realistic load: 200 users, each pausing 0.2–0.8 s between clicks (~300 req/s)

| | With Redis cache | Without cache (`SPRING_CACHE_TYPE=none`) |
|---|---|---|
| Throughput | 293 req/s | 330 req/s |
| p95 latency, all requests | **3.7 ms** | 7.7 ms |
| p95 search | **3.1 ms** | 9.0 ms |
| p95 nearby | **3.1 ms** | 7.6 ms |
| p95 detail | **2.0 ms** | 3.1 ms |
| Errors | 0.003% (1 request) | 0% |
| Cache hit ratio | 98.6% search, 97.2% detail | — |

Both easily meet the targets; at this load the cache roughly halves latency.
(Throughput differs only because response time feeds back into how fast virtual users click.)

### 2. Stress test: 100 users sending requests back to back, no pauses

This finds the maximum throughput, and showed the bottleneck.

| | Before tuning | After caching matches | Change |
|---|---|---|---|
| Throughput | 3,467 req/s | **4,288 req/s** | **+24%** |
| p95 latency, all requests | 13.6 ms | **9.1 ms** | −33% |
| p99 latency, all requests | 48.6 ms | **12.0 ms** | −75% |
| Slowest request | 367 ms | **47 ms** | −87% |
| p95 matches | 54.1 ms | **8.8 ms** | −84% |
| DB requests waiting for a connection (peak) | 24 | **0** | |
| Backend errors | 0 | 0 | |

**What the metrics showed before tuning:** `/api/items/{id}/matches` was the only uncached read. Each call loads up to
500 candidate posts and scores them, so under stress it held database connections much longer than other endpoints.
Grafana's *Database connection pool* panel showed all 10 connections busy with up to 24 requests queued, which also slowed
down every other endpoint (the long p99 tail).

**The fix:** cache match results in Redis for 1 minute, and clear them whenever any post is created, edited, deleted or
changes status (the same rule as search results). Afterwards no request waited for a connection, and the p99 dropped from 49 ms to 12 ms.

### About the small number of failures

In the stress runs 0.03–0.05% of requests failed with `dial: i/o timeout` on the **k6 side**. The backend logged no
errors. These come from Docker Desktop's networking between the k6 container and the Mac when opening thousands of
connections per second, not from the application. A load generator on a separate machine, or k6 installed natively, would avoid it.

## Watching a run live

```bash
cd infra && docker compose --profile monitoring up -d
```

Open Grafana at <http://localhost:3000> (dashboard *Lost & Found*). It shows requests per second and p95 latency per endpoint,
error rate, cache hit ratio, the database connection pool, JVM heap, business activity, and rate-limited requests.
Raw metrics: <http://localhost:8081/actuator/prometheus> (the management port, not reachable through the public port 8080).

## Ideas not done yet

- Run the backend in a container with fixed CPU/memory limits, so results don't depend on what else the laptop is doing.
- A write-heavy scenario (posting and claiming). Logins are rate-limited per IP, so it needs `APP_RATELIMIT_ENABLED=false`
  or a few pre-made accounts.
- Re-test with 100k+ posts to check that the trigram and lat/lng indexes keep search flat as data grows.
