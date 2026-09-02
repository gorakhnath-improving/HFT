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
**Status:** Done.
**Tasks:**
- [x] `git init`, `.gitignore`
- [x] Project memory files (this file, PROGRESS.md, TODO.md, AGENT_CONTEXT.md, DESIGN_DECISIONS.md)
- [x] Maven multi-module parent POM (dependency/version management only, no logic)
- [x] `finex-common` module (shared model/util, no framework deps yet)
- [x] `finex-api` module: minimal Spring Boot app (health endpoint only)
- [x] `docker-compose.yml`: PostgreSQL, Prometheus, Grafana
- [x] Flyway wired into `finex-api` with a placeholder migration, connecting to Dockerized Postgres
- [x] `GET /api/v1/health` returns 200 and reports DB connectivity
- [x] README with build/run instructions
- [x] Root `docs/` skeleton (ARCHITECTURE.md, PROTOCOL.md, PERFORMANCE.md, BENCHMARKS.md,
      FINANCIAL_MODEL.md, DESIGN_DECISIONS.md placeholder — real content later)
**Acceptance criteria:**
- [x] `mvn -q -DskipTests package` succeeds from repo root
- [x] `docker compose up -d postgres` + `mvn -pl finex-api spring-boot:run` boots the app
- [x] `curl localhost:8080/api/v1/health` returns 200 with DB status UP
**Tests:** Smoke test (Spring context loads); Testcontainers Postgres integration test for health check.
**Benchmarks:** None yet.
**Deliverables:** Buildable skeleton, first commit (`ac84774`).

## Phase 2 — Financial Domain Model
**Goal:** Core entities (User, Account, Instrument, Order, Trade, Position, Balance,
LedgerAccount, LedgerEntry) as plain domain objects, framework-agnostic where possible.
**Dependencies:** Phase 1.
**Status:** Done.
**Tasks:**
- [x] ADR-002: Money / fixed-point numeric representation (BigDecimal)
- [x] Enums: Side, OrderType, OrderStatus, InstrumentStatus, AccountStatus, plus
      DebitCredit, EntryType, LedgerAccountType
- [x] `User`, `Account`, `Instrument` records
- [x] `Order`, `Trade` records with validation
- [x] `Position`, `Balance`, `LedgerAccount`, `LedgerEntry` records with validation
- [x] Unit tests for construction, equality, and basic invariants
**Acceptance criteria:**
- [x] `finex-common` contains framework-agnostic domain objects and enums
- [x] `mvn test` passes with new unit tests (15/15 `InstrumentTest`)
- [x] No persistence or service wiring yet
**Tests:** Unit tests for each domain type and its invariants.
**Benchmarks:** None.
**Deliverables:** Domain model in `finex-common`, ADR-002.

## Phase 3 — Correct Order Book
**Goal:** Simple, correct order book (e.g. TreeMap-backed) with price-time priority.
**Dependencies:** Phase 2.
**Status:** Done.
**Tasks:**
- [x] New `finex-order-book` Maven module depending on `finex-common`
- [x] `OrderBook` class using TreeMap for bids (desc) and asks (asc)
- [x] Price-time priority: BUY high price first, SELL low price first, earlier `sequence` wins
- [x] `addOrder`, `cancelOrder`, `bestBid`, `bestAsk`, snapshot views
- [x] Unit tests for priority, cancellation, and determinism
**Acceptance criteria:**
- [x] `mvn test` passes with new order book tests (10/10)
- [x] Same sequence of orders always produces the same book snapshot
- [x] Best bid/ask correctly identifies top of book
**Tests:** `OrderBookTest` with priority, cancellation, and determinism cases.
**Benchmarks:** None yet (JMH comparison of order book structures is Phase 9/17).
**Deliverables:** `finex-order-book` module, ADR-003.

## Phase 4 — Matching Engine
**Goal:** Deterministic price-time-priority matching; full/partial fills; `Trade` generation;
`Order` state updates; determinism tests.
**Dependencies:** Phase 3.
**Status:** Done.
**Tasks:**
- [x] New `finex-matching-engine` Maven module depending on `finex-order-book`
- [x] `MatchingEngine` class per symbol, single-threaded baseline
- [x] `MatchResult` record with final `Order` state, `List<Trade>`, and `addedToBook` flag
- [x] Match incoming order against opposite-side book top using price-time priority
- [x] Full and partial fills of both incoming and resting orders
- [x] `Trade` generation with deterministic `tradeSequence`
- [x] Unfilled LIMIT orders rest; unfilled MARKET orders cancelled
- [x] `MatchingEngineTest` covering full/partial/multiple fills, market orders, limit
      price gating, same-price time priority, and determinism
**Acceptance criteria:**
- [x] `mvn test` passes with new matching engine tests (15/15)
- [x] Same sequence of orders produces the same trades and final book state
- [x] BUY matches lowest ask first; SELL matches highest bid first
- [x] Partially filled resting orders remain in the book with reduced remaining quantity
**Tests:** `MatchingEngineTest` with full/partial/multiple fill, market, limit, priority,
and determinism cases.
**Benchmarks:** None yet.
**Deliverables:** `finex-matching-engine` module, ADR-004.

## Phase 5 — REST/API Layer
**Goal:** Order submit/cancel/query endpoints on top of the matching engine.
**Dependencies:** Phase 4.
**Status:** Done.
**Tasks:**
- [x] Add `finex-matching-engine` dependency to `finex-api`
- [x] Expose `OrderBook.findOrder(long)` for accurate order state lookup
- [x] `OrderService` per-symbol `MatchingEngine` map, global order sequence, in-memory cache
- [x] DTOs: `OrderRequest`, `OrderResponse`, `TradeView`, `OrderBookView`
- [x] `OrderController` with `POST /api/v1/orders`, `DELETE /api/v1/orders/{orderId}`,
      `GET /api/v1/orders/{orderId}`, `GET /api/v1/order-books/{symbol}`
- [x] Request validation (positive quantity, LIMIT price required, valid enums)
- [x] `OrderControllerTest` with `MockMvc` (no database)
- [x] Update `README.md` with curl examples
**Acceptance criteria:**
- [x] `mvn test` passes including new controller tests (7/7)
- [x] `POST /api/v1/orders` creates and matches orders, returns resulting order + trades
- [x] `GET /api/v1/orders/{orderId}` returns current state
- [x] `DELETE /api/v1/orders/{orderId}` cancels an open order
- [x] `GET /api/v1/order-books/{symbol}` returns a snapshot
**Tests:** `OrderControllerTest` covering submit, cancel, query, book snapshot, and a
full/partial fill scenario.
**Benchmarks:** None.
**Deliverables:** Order REST API in `finex-api`.

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
