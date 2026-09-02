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
**Dependencies:** Phase 2, 4, 5.
**Status:** Done.
**Tasks:**
- [x] New `finex-risk` Maven module depending on `finex-common` and `finex-matching-engine`
- [x] `RiskConfig` (size, notional, position, cash, collar, rate-limit limits)
- [x] `AccountRiskState` (cash, position, reservations, order timestamps)
- [x] `RiskResult` (accepted/rejected with reason)
- [x] `RiskEngine` validating size, notional, collar, position, cash, and rate limit
- [x] Reservation of cash (BUY) and projected position on accepted orders
- [x] Update cash/position and release reservations on trades and cancels
- [x] `RiskEngineTest` covering each check and rejection reason
- [x] Integrate `RiskEngine` into `finex-api` `OrderService` (pre-trade validation)
- [x] Update `OrderControllerTest` with rejection cases
- [x] Update `README.md`
**Acceptance criteria:**
- [x] `mvn test` passes including risk engine and updated controller tests
- [x] Orders exceeding configured limits are rejected with `OrderStatus.REJECTED`
- [x] Trades correctly update account cash/positions and release reservations
- [x] Cancellations release remaining reservations
**Tests:** `RiskEngineTest` (10) + updated `OrderControllerTest` (10).
**Benchmarks:** None.
**Deliverables:** `finex-risk` module, risk-integrated `OrderService`.

## Phase 7 — Market Data
**Goal:** BOOK_UPDATE/TRADE/EXECUTION events, snapshot + incremental, async publication.
**Dependencies:** Phase 4, 5, 6.
**Status:** Done.
**Tasks:**
- [x] New `finex-market-data` Maven module depending on `finex-common` and `finex-matching-engine`
- [x] `MarketDataEvent` sealed hierarchy: `BookUpdate`, `TradeEvent`, `ExecutionEvent`
- [x] `PriceLevel` view type for book levels
- [x] `MarketDataPublisher` / `MarketDataListener` interface; synchronous baseline `SimpleMarketDataPublisher`
- [x] Wire `OrderService` to publish `TradeEvent` for each `Trade` and `ExecutionEvent` for each affected order
- [x] Publish `BookUpdate` (full snapshot) after each submit/cancel; incremental deltas will be derived later
- [x] `MarketDataPublisherTest` verifying listener receives trade and book events
**Acceptance criteria:**
- [x] `mvn test` passes including new market-data tests
- [x] Submitting an order that trades produces `TradeEvent` and `ExecutionEvent`s
- [x] `OrderService` publishes a `BookUpdate` after order state changes
- [x] Listener interface is extensible for async publication later
**Tests:** `MarketDataPublisherTest` + `OrderControllerTest`.
**Benchmarks:** None.
**Deliverables:** `finex-market-data` module integrated into `OrderService`.

## Phase 8 — Binary Protocol
**Goal:** Compact binary trading protocol (NEW_ORDER/CANCEL/MODIFY, ACK/REJECT/EXECUTION).
**Dependencies:** Phase 4, 5, 6, 7.
**Status:** Done.
**Tasks:**
- [x] New `finex-protocol` Maven module depending on `finex-common`
- [x] `ProtocolMessage` sealed hierarchy for NEW_ORDER/CANCEL/MODIFY/ACK/REJECT/EXECUTION
- [x] `BinaryCodec` with 4-byte length framing, UTF-8 string fields, plain `BigDecimal` payloads
- [x] `BinaryCodecTest` round-tripping all message types including null price
**Acceptance criteria:**
- [x] `mvn test` passes
- [x] Codec round-trips all message types without allocation overhead beyond necessary
**Tests:** `BinaryCodecTest`.
**Benchmarks:** None.
**Deliverables:** `finex-protocol` module with binary codec.

## Phase 9 — Event Architecture
**Goal:** Append-only event log powering replay.
**Dependencies:** Phase 4, 7, 8.
**Status:** Done.
**Tasks:**
- [x] New `finex-event-log` Maven module depending on `finex-common`, `finex-protocol`
- [x] `Event` record (id, timestamp, type, payload bytes)
- [x] `EventStore` interface: `append(Event)`, `readAll()`
- [x] In-memory `InMemoryEventStore` baseline
- [x] `CommandSerializer` encodes `SubmitOrderCommand` / `CancelOrderCommand` using `BinaryCodec`
- [x] `CommandHandler` interface for replay
- [x] `OrderService` implements `CommandHandler`, appends command events, exposes `eventStore()`
- [x] `ReplayEngine` reconstructs `OrderService` state from events
- [x] `EventStoreTest`, `CommandSerializerTest`, `OrderServiceReplayTest`
**Acceptance criteria:**
- [x] `mvn test` passes including event-log and replay tests
- [x] Running the same command sequence twice via replay produces identical final state
- [x] Event store is append-only; no updates or deletes
**Tests:** `EventStoreTest`, `CommandSerializerTest`, `OrderServiceReplayTest`.
**Benchmarks:** None.
**Deliverables:** `finex-event-log` module, replayable `OrderService`.

## Phase 10 — Symbol Sharding
**Goal:** Multiple independent matching engine shards; scaling measurements.
**Dependencies:** Phase 4, 9.
**Status:** Done.
**Tasks:**
- [x] New `finex-shard` Maven module depending on `finex-common`, `finex-order-book`, `finex-matching-engine`
- [x] `SymbolShardRouter` mapping symbol -> shard id via non-negative modulo of `hashCode`
- [x] `EngineShard` owning per-symbol `MatchingEngine` instances
- [x] `ShardCoordinator` managing a fixed number of shards
- [x] `OrderService` routes submit/cancel to shard by symbol and exposes `replay()`
- [x] `SymbolShardRouterTest` and `OrderServiceShardingTest`
- [x] Fixed `OrderService` order cache by returning `MatchResult.updatedOrders` from `MatchingEngine`
**Acceptance criteria:**
- [x] `mvn test` passes
- [x] Orders for different symbols are routed to independent matching engines
- [x] Replay still works with sharded engines
**Tests:** `SymbolShardRouterTest`, `OrderServiceShardingTest`, `OrderServiceReplayTest`.
**Benchmarks:** None.
**Deliverables:** `finex-shard` module integrated into `OrderService`.

## Phase 11 — Ledger
**Goal:** Double-entry ledger, immutable entries, debits == credits invariant.
**Dependencies:** Phase 2, 4, 7, 10.
**Status:** Done.
**Tasks:**
- [x] New `finex-ledger` Maven module depending on `finex-common`
- [x] `AccountType`, `DebitCredit`, `LedgerAccount`, `LedgerEntry`
- [x] `Ledger` interface: `post(List<LedgerEntry>)`, `entries()`, `balance(accountCode)`
- [x] In-memory `InMemoryLedger` enforcing `sum(debits) == sum(credits)` for every post
- [x] `OrderService` posts trade settlement entries (cash and asset legs for buyer/seller)
- [x] `InMemoryLedgerTest` and `OrderServiceLedgerTest`
**Acceptance criteria:**
- [x] `mvn test` passes including ledger tests
- [x] Every trade creates balanced ledger entries (debits == credits)
- [x] Ledger entries are immutable and append-only
**Tests:** `InMemoryLedgerTest`, `OrderServiceLedgerTest`.
**Benchmarks:** None.
**Deliverables:** `finex-ledger` module integrated into `OrderService`.

## Phase 12 — Portfolio / P&L
**Goal:** Position, avg price, realized/unrealized P&L, exposure, equity.
**Dependencies:** Phase 11.
**Status:** Done.
**Tasks:**
- [x] New `finex-portfolio` Maven module depending on `finex-common`
- [x] `Position` record (symbol, signed quantity, avgPrice, realizedPnl, unrealizedPnl)
- [x] `Portfolio` record per account (cash, positions, totalEquity)
- [x] `PortfolioService` updating positions/cash on every trade, marking to market
- [x] `GET /api/v1/portfolios/{accountId}` endpoint in `PortfolioController`
- [x] `PortfolioServiceTest`, `OrderServicePortfolioTest`
**Acceptance criteria:**
- [x] `mvn test` passes
- [x] Buying increases position and avg price; selling reduces position and realizes PnL
- [x] Unrealized PnL updates when mark price changes
- [x] Total equity = cash + sum(unrealizedPnl)
**Tests:** `PortfolioServiceTest`, `OrderServicePortfolioTest`.
**Benchmarks:** None.
**Deliverables:** `finex-portfolio` module integrated into `OrderService`.

## Phase 13 — Clearing
**Goal:** Buyer/seller obligations, fees, asset/cash movement determination.
**Dependencies:** Phase 4, 11, 12.
**Status:** In progress.
**Tasks:**
- [ ] New `finex-clearing` Maven module depending on `finex-common`
- [ ] `FeeSchedule` (maker/taker fee rates) and `ClearingResult`
- [ ] `ClearingService` computing net cash/asset transfers per trade
- [ ] `OrderService` uses `ClearingService` before ledger posting
- [ ] `ClearingServiceTest`
**Acceptance criteria:**
- [ ] `mvn test` passes
- [ ] Clearing produces balanced cash/asset transfers including fees
- [ ] Fee accrual account is credited on every trade
**Tests:** `ClearingServiceTest`.
**Benchmarks:** None.
**Deliverables:** `finex-clearing` module.

## Phase 14 — Settlement
**Goal:** Simulated settlement lifecycle updating cash/assets/positions/ledger.
**Dependencies:** Phase 13.
**Status:** Not started.
**Tasks:**
- [ ] New `finex-settlement` Maven module depending on `finex-ledger`, `finex-clearing`, `finex-portfolio`
- [ ] `SettlementService` orchestrating: clear trade → post ledger → update portfolio
- [ ] `OrderService` delegates trade settlement to `SettlementService`
- [ ] `SettlementServiceTest` verifying ledger + portfolio update after a trade
**Acceptance criteria:**
- [ ] `mvn test` passes
- [ ] A trade results in consistent ledger, portfolio, and cash/asset state
- [ ] Settlement entries are balanced
**Tests:** `SettlementServiceTest`, `OrderServiceSettlementTest`.
**Benchmarks:** None.
**Deliverables:** `finex-settlement` module integrated into `OrderService`.

## Phase 15 — Replay
**Goal:** Replay event log, verify reconstructed state == original state.
**Dependencies:** Phase 9, 14.
**Status:** Not started.
**Tasks:**
- [ ] Enhance `ReplayEngine` to reconstruct portfolio and ledger snapshots
- [ ] `OrderService.replay()` re-runs events and rebuilds all state
- [ ] `ReplayVerificationTest` asserts original vs replayed order book, positions, ledger balances
**Acceptance criteria:**
- [ ] `mvn test` passes
- [ ] Replaying a sequence of trades yields the same order book, portfolio, and ledger
**Tests:** `ReplayVerificationTest`.
**Benchmarks:** None.
**Deliverables:** State-verified replay.

## Phase 16 — Load Generator
**Goal:** Dedicated Java load generator with configurable workloads.
**Dependencies:** Phase 5, 8, 14.
**Status:** Not started.
**Tasks:**
- [ ] New `finex-load-generator` Maven module depending on `finex-api`, `finex-protocol`
- [ ] `LoadGenerator` with configurable workload (symbols, accounts, order rate, duration)
- [ ] `OrderService` driver that submits orders and collects latency/throughput
- [ ] `LoadGeneratorTest` verifying deterministic output shape (not performance numbers)
**Acceptance criteria:**
- [ ] `mvn test` passes
- [ ] Load generator can be configured and run a fixed number of orders
- [ ] Generates both sides of the book and produces trades
**Tests:** `LoadGeneratorTest`.
**Benchmarks:** None.
**Deliverables:** `finex-load-generator` module.

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
