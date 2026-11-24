# Lost & Found Platform

A full-stack web app for posting **lost** and **found** items, searching them, getting **automatic match
suggestions** between lost and found posts, and returning items through a **verified claim flow**.

**Stack:** Java 25 · Spring Boot 4 · PostgreSQL 16 · Redis 7 · React 19 + TypeScript · Docker · GitHub Actions · Prometheus + Grafana · k6

## Features

- **Accounts.** Register and log in with a short-lived JWT access token (15 min) and a rotating refresh token
  (7 days) in an HttpOnly cookie.
- **Posts.** Lost/found item posts with category, place, optional GPS coordinates, date and a verification question.
  Only the author can edit or delete a post.
- **Photos.** Up to 5 per post, shown as a display size and a thumbnail. Photos are stripped of metadata (including the
  GPS location phones embed) both in the browser and on the server.
- **Search.**
  - Keyword search over title, description and place. It works for Chinese, which has no spaces between words.
  - Filters for type, category and date range.
  - A "near me" filter with a radius, which also shows the distance.
- **Matching.** Each open post lists the most likely posts of the opposite type, with a 0–100 score and the reasons.
  A strong new match notifies the other post's author.
- **Claims.** Someone answers the verification question and sends a message, and the poster accepts or declines.
  - Accepting one claim declines the others, and only then do both people see each other's email.
  - The poster marks the item as returned to close the post.
- **Notifications.** In-app notifications with an unread badge.
- **Abuse protection.**
  - Per-IP rate limits on login, register and refresh.
  - An account lockout after repeated wrong passwords.
  - Logins take the same time whether or not the email exists.

## Run it

You need Docker. From the project root:

```bash
cd infra
cp .env.example .env                                         # then edit: set passwords and a JWT_SECRET
sed -i '' "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48)|" .env   # (macOS) or edit by hand
docker compose --profile app up -d --build
```

Open <http://localhost:8000>. The first build takes a few minutes.

Optional extras:

```bash
docker compose --profile monitoring up -d                     # Grafana → http://localhost:3000
docker exec -i infra-postgres-1 psql -U lostfound -d lostfound < ../load-tests/seed.sql   # 2,000 sample posts
```

## Develop

Run the databases in Docker, and the backend and frontend on your machine. You get hot reload, and the Vite dev
server forwards `/api` to the backend.

```bash
cd infra && docker compose up -d                 # Postgres + Redis
cd backend && ./gradlew bootRun                  # API on :8080, health/metrics on :8081
cd frontend && npm install && npm run dev        # http://localhost:5173
```

Requirements: Java 25, Node 20+, Docker.

## Architecture

```mermaid
flowchart LR
    B[Browser<br/>React SPA] -->|/ and /api| N[nginx]
    N -->|/api| S[Spring Boot]
    S --> P[(PostgreSQL<br/>source of truth)]
    S --> R[(Redis<br/>cache · refresh tokens · rate limits)]
    Pr[Prometheus] -->|:8081/actuator/prometheus| S
    G[Grafana] --> Pr
```

**Backend layout.** Each feature package under `backend/src/main/java/com/lostfound/backend/` has a controller, a
service and a repository, and the API only ever sends DTOs, never JPA entities.

| Package | What it does |
|---|---|
| `auth` | register, login, JWT access tokens, refresh-token rotation |
| `user` | the `users` table, `/api/me` |
| `item` | posts, search (`ItemSpecifications`), caching |
| `match` | scoring (`MatchScorer`, `TextSimilarity`) and top-K selection (`TopK`) |
| `claim` | the claim lifecycle, with a row lock so two approvals can't both win |
| `notification` | in-app notifications |
| `photo` | uploads, image checks and resizing (`ImageProcessor`), storage (`PhotoStorage`) |
| `security` | rate limiting and account lockout (Redis counters) |
| `config` | security, JWT keys, cache setup |

The database schema is managed by Flyway (`backend/src/main/resources/db/migration`).

### How the main parts work

**Keyword search.** Uses `ILIKE '%…%'` backed by **pg_trgm** trigram GIN indexes. Postgres's built-in full-text search
treats "撿到一個咖啡色皮夾" as one word, so a search for "皮夾" would miss it. Trigrams find any part of the text.

**"Near me".** A latitude/longitude box first, which can use an index, then an exact distance check inside the box.
The response includes the Haversine distance.

**Matching.** Candidates are open posts of the opposite type, by someone else, within ±30 days. Each one is scored
0–1 as a weighted sum of four signals:

| Signal | Weight | Rule |
|---|---|---|
| Same category | 35% | all or nothing |
| Time | 20% | linear over 30 days; "found" well before "lost" is penalised |
| Distance | 25% | linear over 5 km; partial credit when coordinates are missing |
| Text | 20% | cosine similarity; Chinese is split into two-character bigrams, English into words |

A size-K min-heap keeps the best results in O(n log k).

**Caching (Redis).**
- Item details are cached for 10 minutes, and search and match results for 1 minute.
- Every write clears the affected entries **after the transaction commits**, so stale data can't be re-cached.
- If Redis is down, the app falls back to the database instead of failing.

**Photos.**
- The browser shrinks each photo to 1600 px, applies its rotation and re-draws it on a canvas, which drops all
  metadata, before uploading.
- The server doesn't trust that. It checks the file's first bytes (only JPEG and PNG), reads the dimensions *before*
  decoding (anything over 40 megapixels is rejected as a decompression bomb), and re-encodes a clean JPEG plus a
  400 px thumbnail.
- File names are server-generated UUIDs. Files go to disk through the `PhotoStorage` interface: a Docker volume today,
  and an S3 implementation could replace it for cloud hosting. Deleting a photo or a post removes its files after the
  database commit.

**Auth.**
- The refresh token is 32 random bytes in an HttpOnly, `SameSite=Strict` cookie scoped to `/api/auth`.
- Redis stores only its SHA-256 hash.
- Each refresh consumes it atomically (`GETDEL`) and issues a new one.
- The frontend refreshes on a 401 and retries the request. Parallel requests share one refresh.

## API

All endpoints are under `/api`. Errors are RFC 7807 problem details (`{"status":404,"detail":"Item not found",…}`).

| Method | Path | Auth | |
|---|---|---|---|
| POST | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout` | – | account and session |
| GET | `/me`, `/me/items`, `/me/claims` | ✓ | the current user's data |
| GET | `/items?q=&type=&categoryId=&status=&from=&to=&lat=&lng=&radiusKm=&page=&size=` | – | search |
| GET | `/items/{id}`, `/items/{id}/matches`, `/categories` | – | details |
| POST / PUT / DELETE | `/items`, `/items/{id}` | ✓ owner | create, edit, delete |
| POST | `/items/{id}/claims` | ✓ | claim someone's post |
| GET | `/items/{id}/claims` | ✓ owner | see claims on your post |
| POST | `/claims/{id}/approve`, `/claims/{id}/reject`, `/items/{id}/returned` | ✓ owner | decide and close |
| GET / POST | `/notifications`, `/notifications/{id}/read`, `/notifications/read-all` | ✓ | notifications |
| POST / DELETE | `/items/{id}/photos` (multipart `file`), `/items/{id}/photos/{photoId}` | ✓ owner | add or remove a photo |
| GET | `/photos/{uuid}.jpg`, `/photos/{uuid}_thumb.jpg` | – | photo files (cached by browsers for a year) |

## Testing

```bash
cd backend && ./gradlew test     # 80 tests: unit + integration (Testcontainers starts real Postgres and Redis)
cd frontend && npm run lint && npm run build
```

- **Unit tests** cover the matching algorithm (tokenizing, scoring rules, and `TopK` checked against a full sort on
  10,000 random numbers) and image processing (resizing, EXIF removal, fake files, decompression bombs).
- **Integration tests** cover every endpoint with MockMvc against real databases, including:
  - permissions (only owners can edit, only posters can approve)
  - the full claim flow
  - cache invalidation
  - refresh-token rotation and reuse
  - rate limiting and account lockout
- **Load tests** use k6 (`load-tests/browse.js`). The latest numbers are in **[docs/perf.md](docs/perf.md)**:
  - about 4,300 requests/s on a laptop
  - p95 latency of 9 ms under stress
  - caching match results cut p99 latency by 75%

CI (GitHub Actions) runs on every push and pull request:
1. Backend tests.
2. Frontend lint and build.
3. Build both Docker images. On `main` they're also pushed to `ghcr.io`.

## Monitoring

`docker compose --profile monitoring up -d` starts Prometheus and Grafana with a ready-made dashboard showing:
- requests per second and p95 latency per endpoint
- error rate
- cache hit ratio
- database connection pool
- JVM heap
- business activity (posts, claims, match notifications)
- rate-limited requests

Metrics are served on the management port **8081**, which is separate from the public port 8080.

## Configuration

| Variable | Default | |
|---|---|---|
| `JWT_SECRET` | dev-only value | **required in Docker**; at least 32 characters. The app refuses to start with a shorter one |
| `REFRESH_COOKIE_SECURE` | `false` | set `true` when served over HTTPS |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | local Postgres | |
| `SPRING_DATA_REDIS_HOST` | `localhost` | |
| `SERVER_FORWARD_HEADERS_STRATEGY` | – | `native` behind a reverse proxy, so rate limits see the real client IP |
| `APP_RATELIMIT_ENABLED` | `true` | turn off for load tests that log in a lot |
| `PHOTO_STORAGE_DIR` | `./data/photos` | where uploaded photos are saved (`/data/photos` volume in Docker) |

## Not done yet

- S3 storage for photos. Photos are on a local volume today, which is fine for one server.
- Frontend automated tests (Vitest, Playwright).
- Deployment to a public host with HTTPS.

## Project structure

```
backend/      Spring Boot API (Dockerfile included)
frontend/     React + TypeScript app, served by nginx in Docker
infra/        docker-compose, Prometheus config, Grafana dashboard
load-tests/   k6 scripts and sample data
docs/         performance results
plan.md       the original project plan
```
