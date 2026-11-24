# Lost & Found Platform — Project Plan

> **Status (2026-11-25):** Phases 0–7 are done. Phase 8 has containers, CI image builds and the README; deploying to a public host is not done yet.
> Still missing: photo upload and frontend automated tests.
> See [README.md](README.md) for how to run it and [docs/perf.md](docs/perf.md) for load-test results.
> The sections below are the original plan, updated where the implementation chose differently.

## 1. Overview

A full-stack web app where people post **lost** or **found** items, search listings, receive automatic **match suggestions**, and complete a **verified claim flow** until an item is returned to its owner.

Goals:

- Clean, layered backend with a well-documented REST API
- Typed, responsive frontend
- Both SQL and NoSQL data stores, each used for what it does best
- Unit, integration, end-to-end and stress testing
- Containerized local stack, automated CI/CD, and runtime monitoring

---

## 2. Tech Stack

| Layer | Choice |
|---|---|
| Backend language | Java 25 |
| Backend framework | Spring Boot 4.x (Web, Validation, Security, Data JPA, Data Redis, Actuator) |
| Build tool | Gradle (Kotlin DSL) |
| API | REST + JSON, OpenAPI / Swagger UI via `springdoc-openapi` |
| Auth | Spring Security + JWT (short-lived access token + refresh token), BCrypt password hashing |
| SQL database | PostgreSQL 16, Spring Data JPA (Hibernate), Flyway migrations |
| NoSQL store | Redis 7: caching, rate limiting, refresh-token store |
| File storage | S3-compatible object storage for item photos (not built yet; the MinIO image is no longer published, so a replacement is still to be chosen) |
| Frontend language | TypeScript |
| Frontend framework | React 19 + Vite |
| Frontend libraries | React Router, TanStack Query, Tailwind CSS, React Hook Form + Zod |
| Backend testing | JUnit 5, Mockito, Spring Boot Test, MockMvc, Testcontainers |
| Frontend testing | Vitest, React Testing Library, Playwright (E2E) |
| Stress / load testing | k6 |
| Containers | Docker (multi-stage builds), docker-compose |
| CI/CD | GitHub Actions, images pushed to GitHub Container Registry |
| Observability | Spring Boot Actuator, Micrometer → Prometheus, Grafana, structured JSON logs |

---

## 3. Repository Structure

```
lost-and-found-platform/
├── backend/                    # Spring Boot app
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/lostfound/
│       │   ├── auth/           # login, register, JWT, refresh tokens
│       │   ├── user/           # profile, roles
│       │   ├── item/           # item posts, search
│       │   ├── claim/          # claim + verification flow
│       │   ├── match/          # matching algorithm
│       │   ├── notification/   # in-app notifications
│       │   ├── storage/        # photo upload (S3/MinIO)
│       │   └── common/         # config, errors, security, utils
│       ├── main/resources/
│       │   ├── application.yml
│       │   └── db/migration/   # Flyway SQL scripts
│       └── test/java/...       # unit + integration tests
├── frontend/                   # React + TypeScript (Vite)
│   └── src/
│       ├── api/                # typed API client
│       ├── components/
│       ├── features/           # auth, items, claims, profile
│       ├── pages/
│       └── tests/
├── load-tests/                 # k6 scripts
├── infra/
│   ├── docker-compose.yml      # postgres, redis, minio, backend, frontend
│   ├── prometheus.yml
│   └── grafana/                # dashboards
├── .github/workflows/ci.yml
├── docs/                       # ERD, API notes, ADRs, perf results
├── .env.example
├── plan.md
└── README.md
```

The backend is organized **package-by-feature**. Each feature uses `Controller → Service → Repository` layers, and DTOs are mapped at the API boundary so JPA entities are never exposed directly.

---

## 4. Core Features (MVP)

1. **Accounts**
   - Register, log in, refresh token, log out
   - Profile and "my posts"
   - Roles: `USER`, `ADMIN`
2. **Item posts**
   - Type: `LOST` / `FOUND`
   - Title, description, category, location (text + lat/lng), date lost/found, photos
   - Status lifecycle: `OPEN → CLAIMED → RETURNED`, or `CLOSED`
3. **Search & filter**
   - Keyword (`ILIKE` with pg_trgm trigram indexes; Postgres full-text search can't split Chinese words)
   - Filter by type, category, date range, and distance from a point
   - Pagination and sorting (newest, closest)
4. **Automatic matching**
   - When an item is posted, score it against `OPEN` items of the opposite type
   - Score = weighted sum of:
     - category match
     - time proximity
     - geographic distance (Haversine)
     - text similarity (cosine over word counts; Chinese split into two-character bigrams)
   - Return the top-K candidates using a min-heap (priority queue)
5. **Claim flow**
   - The finder sets a private verification question (e.g. "What sticker is on the back?")
   - The claimant submits an answer and a message
   - The finder approves or rejects; the item status updates automatically
   - In-app notifications to both sides
6. **Moderation basics**
   - Users can report a post
   - Admins can hide posts and review reports

---

## 5. Data Model

### PostgreSQL (source of truth)

| Table | Key columns |
|---|---|
| `users` | id, email (unique), password_hash, display_name, role, created_at |
| `categories` | id, name |
| `items` | id, owner_id → users, type, title, description, category_id, location_text, lat, lng, event_date, status, verification_question, created_at, updated_at |
| `item_photos` | id, item_id → items, object_key, sort_order |
| `claims` | id, item_id → items, claimant_id → users, answer, message, status, created_at, decided_at |
| `notifications` | id, user_id → users, type, payload (jsonb), read_at, created_at |
| `reports` | id, item_id → items, reporter_id → users, reason, status |

Indexes: trigram GIN on `title`, `description`, `location_text`; B-tree on `(type, status, category_id)`, `event_date`, and `(lat, lng)`.

### Redis (NoSQL)

| Key pattern | Purpose | TTL |
|---|---|---|
| `cache:item:{id}` | Item detail cache | 10 min |
| `cache:search:{hash}` | Popular search result pages | 1–2 min |
| `rate:{ip}:{route}` | Rate limiting (fixed-window counter) | 1 min |
| `refresh:{userId}:{tokenId}` | Valid refresh tokens (revocable) | 7 days |

Cache entries are invalidated when an item is updated or its status changes.

---

## 6. API Sketch

| Method & path | Description |
|---|---|
| `POST /api/auth/register`, `/login`, `/refresh`, `/logout` | Authentication |
| `GET /api/me`, `GET /api/me/items`, `GET /api/me/claims` | Current user |
| `GET /api/items?q=&type=&category=&from=&to=&lat=&lng=&radius=&page=&sort=` | Search |
| `POST /api/items`, `GET/PUT/DELETE /api/items/{id}` | Item CRUD |
| `POST /api/items/{id}/photos` | Photo upload |
| `GET /api/items/{id}/matches` | Match suggestions |
| `POST /api/items/{id}/claims` | Submit a claim |
| `POST /api/claims/{id}/approve`, `/reject` | Decide on a claim |
| `POST /api/items/{id}/returned` | Mark as returned |
| `GET /api/notifications`, `POST /api/notifications/{id}/read` | Notifications |
| `POST /api/items/{id}/reports`, `GET /api/admin/reports` | Moderation |

Errors use RFC 7807 `ProblemDetail` responses through a global `@RestControllerAdvice`.

---

## 7. Testing Strategy

| Level | Tools | Scope |
|---|---|---|
| Unit (backend) | JUnit 5, Mockito | Services, matching scorer, top-K selection, JWT utils |
| Unit (frontend) | Vitest, React Testing Library | Components, hooks, form validation |
| Integration | `@SpringBootTest`, MockMvc, Testcontainers (PostgreSQL, Redis) | Repositories, Flyway migrations, security rules, API contracts, cache behaviour |
| End-to-end | Playwright | Register → post item → search → claim → approve → returned |
| Stress / load | k6 | Ramping virtual users on search and item creation; spike test on login |

Targets:

- ≥ 80% line coverage on service classes
- Search p95 latency < 300 ms under the defined load, error rate < 1%
- Record before/after results in `docs/perf.md`

---

## 8. CI/CD & Operations

**Pull request workflow**
1. Backend: `./gradlew check` (Spotless format check, unit + integration tests with Testcontainers)
2. Frontend: `npm ci && npm run lint && npm run typecheck && npm test`
3. Docker build for both images (no push)

**Main branch workflow**
1. Everything above
2. Build and push images to GHCR, tagged with the commit SHA and `latest`
3. Optional deploy (Render / Fly.io / a VM running docker-compose)

**Monitoring**
- `/actuator/health` and `/actuator/prometheus` endpoints
- Prometheus scrapes the backend; a Grafana dashboard shows request rate, p95 latency, error rate, cache hit ratio, DB pool usage and JVM memory/GC
- JSON logs with a request ID for tracing

**Performance tuning checklist**
- Query plans (`EXPLAIN ANALYZE`) for search
- Avoid N+1 queries (fetch joins / entity graphs)
- Redis cache hit ratio
- Connection pool sizing (HikariCP)
- Compare k6 runs before and after each change

---

## 9. Milestones

| Phase | Scope | Done when |
|---|---|---|
| 0 | Scaffold backend + frontend, docker-compose (Postgres, Redis), CI skeleton | `docker compose up` runs and CI is green |
| 1 | Auth + users | Register/login works; security tests pass |
| 2 | Item CRUD + photo upload | Items can be created, edited and viewed with photos |
| 3 | Search & filter + Redis cache | Filtered search works; cache integration tests pass |
| 4 | Matching algorithm | Match suggestions appear; scorer unit tests pass |
| 5 | Claim flow + notifications | Full claim lifecycle works end to end |
| 6 | Frontend polish + Playwright E2E | Main user journey covered by E2E tests |
| 7 | k6 stress tests, Grafana dashboards, performance tuning | Targets met and documented in `docs/perf.md` |
| 8 | Deployment + README / docs | Public demo URL and setup guide |

---

## 10. Conventions

- **Commits:** Conventional Commits (`feat:`, `fix:`, `test:`, `chore:` …)
- **Branching:** feature branches → pull requests → `main`
- **Formatting:** Spotless (Java, Google Java Format); ESLint + Prettier (TypeScript); `.editorconfig`
- **Config:** `application.yml` with profiles (`local`, `test`, `prod`); secrets only via environment variables; `.env.example` committed, `.env` git-ignored
- **Docs:** decisions recorded as short ADRs in `docs/adr/`
