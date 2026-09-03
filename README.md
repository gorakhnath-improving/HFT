# FinEx

High-performance simulated financial exchange and trading infrastructure platform, built to
demonstrate both **FinTech correctness** (ledger, risk, clearing, settlement, audit) and
**HFT-oriented systems engineering** (low-latency matching, concurrency, benchmarking,
profiling). Full scope and roadmap: [`PROJECT_PLAN.md`](PROJECT_PLAN.md) (derived from
[`../Master Plan.md`](../Master%20Plan.md)).

> **Status:** Phases 17-22 completed. Benchmarks (JMH), profiling (JFR), an evidence-driven
> optimization pass, Micrometer/Prometheus observability, chaos-style failure tests, and
> API-key account isolation are now integrated. See [`AGENT_CONTEXT.md`](AGENT_CONTEXT.md)
> for the current task and [`PROGRESS.md`](PROGRESS.md) for the session log.

## Project layout

```text
finex/
├── finex-common/          shared domain model & utilities (framework-agnostic)
├── finex-order-book/      price-time-priority order book
├── finex-matching-engine/ deterministic matching engine
├── finex-risk/            pre-trade risk engine
├── finex-market-data/     market-data events (book, trade, execution)
├── finex-protocol/        compact binary trading protocol codec
├── finex-event-log/       append-only command events and replay
├── finex-shard/           symbol sharding and engine shards
├── finex-ledger/          double-entry ledger
├── finex-portfolio/       positions, P&L, and equity
├── finex-clearing/        trade clearing and fee schedule
├── finex-settlement/      settlement orchestration
├── finex-load-generator/  configurable order-load harness
├── finex-benchmarks/      JMH micro/component/end-to-end benchmarks and JFR profiling
├── finex-api/             Spring Boot REST app, metrics, and API-key security
├── docker/                config for containerized infra (prometheus, grafana)
├── docker-compose.yml     infra dependencies: postgres, prometheus, grafana
├── docs/                  architecture, protocol, financial model, performance, security
├── PROJECT_PLAN.md        phased roadmap
├── PROGRESS.md            session-by-session log
├── TODO.md                active task queue (current phase only)
├── AGENT_CONTEXT.md       short-form current state for fast pickup
└── DESIGN_DECISIONS.md    ADRs
```

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

## Trading API examples

Trading endpoints require the `X-API-Key` header for the account in the request body
(your environment must register the key, see `ApiKeyService`). Public endpoints such as
order books and actuators do not require a key.

Submit a limit sell for account `100`:

```bash
curl -s -X POST localhost:8080/api/v1/orders \
  -H 'X-API-Key: your-key-for-account-100' \
  -H 'Content-Type: application/json' \
  -d '{"clientOrderId":"sell-1","symbol":"BTC-USD","side":"SELL","type":"LIMIT","price":"50000","quantity":"1","accountId":100}' | jq
```

Submit a matching limit buy for account `200`:

```bash
curl -s -X POST localhost:8080/api/v1/orders \
  -H 'X-API-Key: your-key-for-account-200' \
  -H 'Content-Type: application/json' \
  -d '{"clientOrderId":"buy-1","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":"50000","quantity":"1","accountId":200}' | jq
```

Query an order:

```bash
curl -s localhost:8080/api/v1/orders/1 \
  -H 'X-API-Key: your-key-for-account-100' | jq
```

Cancel an order:

```bash
curl -s -X DELETE localhost:8080/api/v1/orders/1 \
  -H 'X-API-Key: your-key-for-account-100' | jq
```

Get an order-book snapshot (public):

```bash
curl -s localhost:8080/api/v1/order-books/BTC-USD | jq
```

A rejected order returns `400 Bad Request` with `status: REJECTED` and a `rejectionReason`:

```bash
curl -s -X POST localhost:8080/api/v1/orders \
  -H 'X-API-Key: your-key-for-account-100' \
  -H 'Content-Type: application/json' \
  -d '{"clientOrderId":"big","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":"50000","quantity":"1000","accountId":100}' | jq
```

## Metrics

Prometheus-compatible metrics are exposed on `/actuator/prometheus`:

```bash
curl -s localhost:8080/actuator/prometheus | grep finex
```

Grafana is configured at http://localhost:3000 with a sample FinEx dashboard.

## Benchmarks

Run the JMH suite:

```bash
mvn -pl finex-benchmarks -DskipTests package exec:java \
  -Dexec.mainClass=com.finex.benchmarks.BenchmarkRunner
```

Capture a JFR profile:

```bash
mvn -pl finex-benchmarks exec:java \
  -Dexec.mainClass=com.finex.benchmarks.ProfileRunner \
  -Dexec.args="/tmp/finex-profile.jfr"
```

## Risk limits

The baseline `RiskEngine` enforces per-account limits before an order reaches the book:

| Limit               | Default | Checked for |
|---------------------|---------|-------------|
| Max order quantity  | 1000    | both sides  |
| Max order notional  | 500000  | both sides  |
| Max position        | 100     | both sides (long/short absolute) |
| Max cash exposure   | 500000  | BUY total open notional |
| Initial cash        | 1000000 | BUY available cash |
| Price collar        | 10%     | LIMIT orders vs last trade price |
| Max orders/second   | 10      | per account |

## Test

```bash
mvn test
```

Integration tests use [Testcontainers](https://testcontainers.com/) and require Docker to
be running; they spin up an ephemeral PostgreSQL container per test class. The order
controller tests run with `MockMvc` and do not require a database.

## Stop infrastructure

```bash
docker compose down
```
