# FinEx

High-performance simulated financial exchange and trading infrastructure platform, built to
demonstrate both **FinTech correctness** (ledger, risk, clearing, settlement, audit) and
**HFT-oriented systems engineering** (low-latency matching, concurrency, benchmarking,
profiling). Full scope and roadmap: [`PROJECT_PLAN.md`](PROJECT_PLAN.md) (derived from
[`../Master Plan.md`](../Master%20Plan.md)).

> **Status:** Phase 1 — Repository Bootstrap. Only a health-check skeleton exists so far;
> no trading functionality yet. See [`AGENT_CONTEXT.md`](AGENT_CONTEXT.md) for the current
> task and [`PROGRESS.md`](PROGRESS.md) for the session log.

## Project layout

```text
finex/
├── finex-common/     shared domain model & utilities (framework-agnostic)
├── finex-api/         Spring Boot REST app (administrative/developer-facing, not the hot path)
├── docker/            config for containerized infra (prometheus, grafana)
├── docker-compose.yml infra dependencies: postgres, prometheus, grafana
├── PROJECT_PLAN.md    phased roadmap
├── PROGRESS.md        session-by-session log
├── TODO.md            active task queue (current phase only)
├── AGENT_CONTEXT.md   short-form current state for fast pickup
└── DESIGN_DECISIONS.md ADRs
```

More modules (`finex-order-book`, `finex-matching-engine`, `finex-risk`, `finex-ledger`, ...)
are added as their phases begin.

## Prerequisites

- Java 25 (LTS)
- Maven 3.9+
- Docker + Docker Compose

## Build

```bash
mvn -q -DskipTests package
```

## Run infrastructure

```bash
docker compose up -d postgres prometheus grafana
```

- PostgreSQL: `localhost:5432` (db/user/password: `finex` — override via `.env`, see below)
- Prometheus: http://localhost:9090
- Grafana: http://localhost:3000 (admin/admin, anonymous viewing enabled)

Optional `.env` file at the repo root to override defaults:

```bash
DB_NAME=finex
DB_USER=finex
DB_PASSWORD=finex
DB_PORT=5432
```

## Run the API

```bash
mvn -pl finex-api spring-boot:run
```

Check health (reports DB connectivity):

```bash
curl -s localhost:8080/api/v1/health | jq
```

## Test

```bash
mvn test
```

Integration tests use [Testcontainers](https://testcontainers.com/) and require Docker to
be running; they spin up an ephemeral PostgreSQL container per test class.

## Stop infrastructure

```bash
docker compose down
```
