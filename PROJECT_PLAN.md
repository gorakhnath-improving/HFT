# FinEx — Project Plan

Source of truth for scope: `../Master Plan.md`. This file tracks the phased roadmap and
the acceptance criteria for each phase. Keep tasks atomic (see Master Plan §44).

Build tool: **Maven** (multi-module reactor). Containerized dependencies: **Docker Compose**
(PostgreSQL, Prometheus, Grafana — more added as needed). Java 25 LTS, Spring Boot.

## Phase 0 — Requirements & Architecture
**Goal:** Understand scope, pick baseline stack, avoid premature optimization.
**Status:** Done (this bootstrap session).
- [x] Read Master Plan
- [x] Choose build tool (Maven)
- [x] Choose containerization approach (Docker Compose for infra deps)
- [x] Define module layout (below)

## Phase 1 — Repository Bootstrap
**Goal:** A buildable, runnable skeleton with no business logic — proves the toolchain works.
**Dependencies:** Phase 0.
**Tasks:**
- [x] `git init`, `.gitignore`
- [x] Project memory files (this file, PROGRESS.md, TODO.md, AGENT_CONTEXT.md, DESIGN_DECISIONS.md)
- [ ] Maven multi-module parent POM (dependency/version management only, no logic)
- [ ] `finex-common` module (shared model/util, no framework deps yet)
- [ ] `finex-api` module: minimal Spring Boot app (health endpoint only)
- [ ] `docker-compose.yml`: PostgreSQL, Prometheus, Grafana
- [ ] Flyway wired into `finex-api` with a placeholder migration, connecting to Dockerized Postgres
- [ ] `GET /api/v1/health` returns 200 and reports DB connectivity
- [ ] README with build/run instructions
- [ ] Root `docs/` skeleton (ARCHITECTURE.md, PROTOCOL.md, PERFORMANCE.md, BENCHMARKS.md,
      FINANCIAL_MODEL.md, DESIGN_DECISIONS.md placeholder — real content later)
**Acceptance criteria:**
- `mvn -q -DskipTests package` succeeds from repo root
- `docker compose up -d postgres` + `mvn -pl finex-api spring-boot:run` boots the app
- `curl localhost:8080/api/v1/health` returns 200 with DB status UP
**Tests:** Smoke test (Spring context loads); Testcontainers Postgres integration test for health check.
**Benchmarks:** None yet.
**Deliverables:** Buildable skeleton, first commit(s).

## Phase 2 — Financial Domain Model
**Goal:** Core entities (User, Account, Instrument, Order, Trade, Position, Balance,
LedgerAccount, LedgerEntry) as plain domain objects, framework-agnostic where possible.
**Dependencies:** Phase 1.
**Tasks:** TBD — will be broken into atomic tasks when this phase starts.

## Phase 3 — Correct Order Book
**Goal:** Simple, correct order book (e.g. TreeMap-backed) with price-time priority.
**Dependencies:** Phase 2.

## Phase 4 — Matching Engine
**Goal:** Deterministic price-time-priority matching; full/partial fills; determinism tests.
**Dependencies:** Phase 3.

## Phase 5 — REST/API Layer
**Goal:** Order submit/cancel/query endpoints on top of the matching engine.
**Dependencies:** Phase 4.

## Phase 6 — Risk Engine
**Goal:** Pre-trade risk checks (size, notional, collar, position, exposure, rate limit).
**Dependencies:** Phase 2, 4.

## Phase 7 — Market Data
**Goal:** BOOK_UPDATE/TRADE/EXECUTION events, snapshot + incremental, async publication.
**Dependencies:** Phase 4.

## Phase 8 — Binary Protocol
**Goal:** Compact binary trading protocol (NEW_ORDER/CANCEL/MODIFY, ACK/REJECT/EXECUTION).
**Dependencies:** Phase 4, 5.

## Phase 9 — Event Architecture
**Goal:** Append-only event log powering replay.
**Dependencies:** Phase 4.

## Phase 10 — Symbol Sharding
**Goal:** Multiple independent matching engine shards; scaling measurements.
**Dependencies:** Phase 4, 25 (concurrency model).

## Phase 11 — Ledger
**Goal:** Double-entry ledger, immutable entries, debits == credits invariant.
**Dependencies:** Phase 2.

## Phase 12 — Portfolio / P&L
**Goal:** Position, avg price, realized/unrealized P&L, exposure, equity.
**Dependencies:** Phase 11.

## Phase 13 — Clearing
**Goal:** Buyer/seller obligations, fees, asset/cash movement determination.
**Dependencies:** Phase 4, 11.

## Phase 14 — Settlement
**Goal:** Simulated settlement lifecycle updating cash/assets/positions/ledger.
**Dependencies:** Phase 13.

## Phase 15 — Replay
**Goal:** Replay event log, verify reconstructed state == original state.
**Dependencies:** Phase 9.

## Phase 16 — Load Generator
**Goal:** Dedicated Java load generator with configurable workloads.
**Dependencies:** Phase 5, 8.

## Phase 17 — Performance Benchmarks
**Goal:** JMH microbenchmarks, component benchmarks, end-to-end benchmarks.
**Dependencies:** Phase 4, 16.

## Phase 18 — Profiling
**Goal:** JFR/async-profiler/perf investigation of hotspots.
**Dependencies:** Phase 17.

## Phase 19 — Performance Optimization
**Goal:** Evidence-driven optimization cycles, documented in docs/performance/.
**Dependencies:** Phase 18.

## Phase 20 — Observability
**Goal:** Micrometer + Prometheus metrics, Grafana dashboards.
**Dependencies:** Phase 1 (infra), ongoing.

## Phase 21 — Failure Testing
**Goal:** Chaos-style tests for invalid input, outages, restarts, corrupt/duplicate events.
**Dependencies:** Most core phases.

## Phase 22 — Security
**Goal:** AuthN/AuthZ, account isolation, API keys, rate limiting, safe parsing.
**Dependencies:** Phase 5, 8.

## Phase 23 — Documentation
**Goal:** ARCHITECTURE.md, PROTOCOL.md, FINANCIAL_MODEL.md, DESIGN_DECISIONS.md finalized.
**Dependencies:** Most phases.

## Phase 24 — Final Benchmark Campaign
**Goal:** Full performance test matrix (cores x symbols x workloads), honest 1M/sec report.
**Dependencies:** All prior phases.

---
Each phase's atomic tasks will be expanded into TODO.md just before that phase starts,
per Master Plan §48 (token/context efficiency) — we don't pre-expand every phase now.
