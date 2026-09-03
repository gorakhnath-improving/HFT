# FinEx

**FinEx is a portfolio-grade simulation of financial trading infrastructure**, built in Java
and Spring Boot, combining FinTech financial correctness with high-performance systems
engineering practice. It is a serious engineering simulation and performance-research project —
it is **not** a production exchange, not institutionally certified, and not a claim that a
Spring Boot application itself runs at HFT speeds.

The architecture deliberately separates two planes with different priorities:

- A **Spring Boot control plane** — REST API, API-key security, Prometheus/Grafana
  observability — that prioritizes correctness and operability.
- A **performance-oriented trading plane** — matching engine, order book, risk engine, binary
  protocol, event log — that prioritizes low allocation, determinism, and *measured*
  throughput/latency, built and validated through 13 evidence-driven optimization experiments.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the system diagram and
[`docs/performance/OPTIMIZATION_JOURNEY.md`](docs/performance/OPTIMIZATION_JOURNEY.md) for the
performance-engineering narrative.

## What FinEx demonstrates

**FinTech correctness**
- Order management with pre-trade risk checks (quantity, notional, position, cash exposure,
  price collar, rate limiting)
- Double-entry ledger with balance validation on every posting
- Trade clearing (maker/taker fees) and settlement orchestration
- Portfolio positions, realized/unrealized P&L
- Full audit trail via an append-only event log

**Performance engineering**
- 13 numbered, evidence-driven optimization experiments (`docs/performance/OPTIMIZATIONS.md`),
  each backed by a JFR profile or a controlled, interleaved A/B benchmark — including two
  optimizations that were **rejected or downgraded** because their own evidence didn't support
  the hypothesis (see `docs/performance/OPTIMIZATION_JOURNEY.md`)
- A selectable, checked fixed-point numeric mode for the risk/clearing hot path, with
  `BigDecimal` retained as the default and correctness reference
- JMH micro-benchmarks and a long-running, low-variance sustained-throughput driver
- JFR-based CPU/allocation/GC profiling used to justify every change

**Reliability**
- Deterministic, replayable event sourcing (`ReplayEngine`): a fresh service instance can
  reconstruct exact trading state from the event log alone
- A randomized differential/financial-invariant stress harness, validated from 10 up to
  1,000,000 generated commands across 7 workload profiles, including exact
  BigDecimal-vs-fixed-point equivalence checking
- Five financial invariants enforced on every run: cash conservation, asset conservation,
  ledger double-entry balance, order-quantity conservation, account isolation

> **Status: FINALIZED / PORTFOLIO COMPLETE.** All 24 foundation phases and 13 optimization
> experiments are complete; `mvn test` is green across all 16 modules. See
> [`AGENT_CONTEXT.md`](AGENT_CONTEXT.md) for the current state summary,
> [`PROGRESS.md`](PROGRESS.md) for the full session log, and
> [`docs/FUTURE_RESEARCH.md`](docs/FUTURE_RESEARCH.md) for evidence-ranked future directions
> that were deliberately **not** started. Full scope and phased roadmap:
> [`PROJECT_PLAN.md`](PROJECT_PLAN.md).

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
├── docs/                  architecture, protocol, financial model, performance, security,
│                          future research (see docs/FUTURE_RESEARCH.md)
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

## Design highlights

A few of the more interesting engineering decisions, expanded in
[`docs/performance/OPTIMIZATION_JOURNEY.md`](docs/performance/OPTIMIZATION_JOURNEY.md) and
[`DESIGN_DECISIONS.md`](DESIGN_DECISIONS.md):

- **Deterministic matching** — strict price-time priority; the same command sequence always
  produces the same trades, regardless of how many times it's replayed.
- **Owner-serialized state where it matters** — `FixedPointAccountRiskState` is documented as
  single-writer, which is precisely what let OPT-012/013 safely replace its internal
  `ConcurrentHashMap`s with a purpose-built primitive table (a concurrent map was paying for
  concurrency guarantees nothing used).
- **Fixed-point arithmetic, selectively** — a checked, scale-4 `long` numeric type
  (`FixedPoint`) is available as an opt-in mode on the risk/clearing hot path only, after
  profiling justified it and a differential-equivalence harness validated it against the
  `BigDecimal` reference. It is not a blanket replacement.
- **`BigDecimal` retained as the reference** — matching, order book, ledger, portfolio, and the
  public API all still use `BigDecimal`, by design; converting them "for consistency" without
  profiling evidence was explicitly avoided.
- **Do-nothing-you-don't-have-to** — the two largest early wins (OPT-002, OPT-003) were both
  about skipping unconditional work (market-data snapshots with no subscribers, mark-to-market
  scans when the price hasn't moved), not new algorithms.
- **Replay as a first-class property** — every submit/cancel is an event; a fresh
  `OrderService` can rebuild the exact final state (order books, ledger, portfolios) from the
  event log alone. This surfaced and fixed a real pre-existing bug (see OPT-009 in
  `docs/performance/OPTIMIZATIONS.md`).
- **Financial invariants as a correctness gate, not an afterthought** — no numeric or
  concurrency optimization was accepted without passing cash/asset/ledger/quantity/account
  invariant checks across randomized workloads first.

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

Run the long, low-variance sustained-throughput driver used for all optimization evidence
(see [`docs/performance/BENCHMARKS.md`](docs/performance/BENCHMARKS.md) for full methodology
and [`docs/performance/FINAL_BENCHMARK_REPORT.md`](docs/performance/FINAL_BENCHMARK_REPORT.md)
for the honest, non-cherry-picked final numbers — including a documented case where the same
code measured 3-4x lower throughput on a loaded machine):

```bash
mvn -q -DskipTests install
mvn -q -pl finex-benchmarks dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
java -cp "finex-benchmarks/target/classes:$(cat /tmp/cp.txt)" \
  com.finex.benchmarks.SustainedSharedServiceDriver 1500000 500 FIXED_POINT
```

Run the randomized differential/financial-invariant stress harness:

```bash
mvn -pl finex-benchmarks test -Dtest=StressHarnessTest   # fast, part of mvn test
java -cp "finex-benchmarks/target/classes:$(cat /tmp/cp.txt)" \
  com.finex.benchmarks.stress.StressDriver 7 ALL 100000  # large-scale manual run
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
