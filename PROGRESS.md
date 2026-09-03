# PROGRESS LOG

Reverse-chronological. One entry per session/significant milestone.

---

## 2026-09-03 — Session 13: OPT-004 + OPT-005 — Latency measurement and O(1) risk reservation totals

**Scope:** Continuation of the post-master-plan performance engineering pass; same
measure-first methodology as previous sessions.

**Done:**
- Created `docs/performance/OPTIMIZATION_PLAN.md` with a prioritized, evidence-driven
  backlog (latency measurement, allocation reduction, fixed-point numerics, lock-free
  order book, async settlement, etc.) and explicit stopping conditions.
- **OPT-004:** Added per-order `System.nanoTime()` latency measurement to
  `SustainedSharedServiceDriver`. Output now reports p50/p90/p99/p99.9/p99.99/max in
  nanoseconds alongside throughput. Added regression assertions in
  `SustainedSharedServiceDriverTest`.
- Re-profiled the post-OPT-004 baseline with JDK Flight Recorder (`settings=profile`)
  on the sustained 1.5M-order driver. Identified the next dominant bottleneck:
  `AccountRiskState.reservedCash()` / `reservedPosition()` were doing a full
  `ConcurrentHashMap.values().stream().reduce(...)` on every `RiskEngine.validate`
  call, repeatedly allocating `BigDecimal` sums for all open orders.
- **OPT-005:** Added running `totalReservedCash` and `totalReservedPosition` fields to
  `AccountRiskState`, updated incrementally on `reserveOrder`, `releaseOrder`, and
  `applyTrade`. `reservedCash()` / `reservedPosition()` are now O(1) and return the
  cached totals; `availableCash()` and `projectedPosition()` use them directly.
- Added `runningReservationTotalsAreConsistentAcrossMultipleOrders` test to
  `RiskEngineTest`.
- Measured before/after on the sustained driver (3 runs each):
  - Before (post-OPT-004): 137,562.51 ops/sec average
  - After (post-OPT-005): 587,705.20 ops/sec average
  - Delta: **+450,142.69 ops/sec, +327.2%**
  - Cumulative vs original baseline (pre-OPT-002): **+659.8%**
  Order-level throughput: **≈ 11.75M orders/sec** on a single shared `OrderService`.
- Latency results:
  - p50: ~2.9 µs → ~1.1 µs (≈ 60% reduction)
  - p99: ~28.5 µs → ~5.9 µs (≈ 79% reduction)
  - p99.9: ~49 µs → ~28 µs (≈ 43% reduction)
- Re-profiled after OPT-005: `AccountRiskState` methods no longer appear in top CPU or
  allocation samples. `BigDecimal.valueOf` allocation samples dropped from ~486-650 per
  1.5M-order run to ~144 per run. The dominant remaining CPU/allocation frames are now
  `MatchingEngine.placeOrder`, `InMemoryLedger.post`, `SettlementService.settle`,
  `CommandSerializer.toEvent`, and `BinaryCodec.encode`.
- Updated `docs/performance/OPTIMIZATION_PLAN.md` (status), `OPTIMIZATIONS.md`
  (OPT-004 and OPT-005 entries), `OPTIMIZATION_EVIDENCE.md` (raw data and JFR
  findings), `BENCHMARKS.md` (post-OPT-005 numbers and latency table),
  `FINAL_BENCHMARK_REPORT.md` (new verdict and roadmap), `AGENT_CONTEXT.md`,
  `TODO.md`.

**Verified:**
- `mvn test` — SUCCESS across all 16 modules.
- Differential check: identical order/trade counts (1,500,000 → 750,000) and no change
  in final portfolio/ledger/cash behavior on the deterministic workload.

**Blockers:** None.

**Next session should:**
- Tackle the next evidence-backed hotspot: event-log/ledger per-trade allocation
  (`SettlementService.settle`, `InMemoryLedger.post`, `CommandSerializer.toEvent`,
  `BinaryCodec.encode`). Profile first, then make a minimal, correctness-preserving
  change. Continue down `OPTIMIZATION_PLAN.md`.

---

## 2026-09-03 — Session 12: OPT-003 — Evidence-driven optimization (mark-to-market skip on unchanged price)

**Scope:** Continuation of the post-master-plan performance engineering pass; same
measure-first methodology as Session 11.

**Done:**
- Re-profiled the post-OPT-002 baseline with the committed
  `SustainedSharedServiceDriver` and JDK Flight Recorder (`settings=profile`).
- Identified the new top `com.finex.*` CPU hotspot: `com.finex.portfolio.Position.mark`
  (112/1243 sampled leaf frames, 9.0%), called from
  `PortfolioService.markToMarket(symbol, trade.price())` on every trade.
- Root cause: `SettlementService.settle` called `markToMarket` after every trade with
  the trade price as the mark price, and `PortfolioService.markToMarket` re-scanned
  every account's position for that symbol even when the mark price was identical to
  the previous mark. In the sustained driver, prices alternate in blocks of 500 orders,
  so the same mark price was repeated 500 times per block.
- **OPT-003:** added a `lastMarkPrices` cache to `PortfolioService`. `markToMarket`
  returns immediately when the requested mark price equals the cached last mark price
  for that symbol; otherwise it stores the new price and revalues all positions as
  before. `Position.withTrade` (called by `applyTrade`) already computes the traded
  accounts' `unrealizedPnl` at the mark price, so no extra work is needed when the
  price is unchanged.
- Added regression tests in `PortfolioServiceTest`:
  - `markToMarketRevaluesPositionsWhenPriceChanges`
  - `markToMarketIsIdempotentAtSamePrice`
  - `markToMarketAtSamePriceStillCorrectlyUpdatesNewPosition`
- Measured before/after on the sustained driver (3 runs each):
  - Before (post-OPT-002): 98,944.11 ops/sec average
  - After (post-OPT-003): 137,562.51 ops/sec average
  - Delta: **+38,618.40 ops/sec, +39.0%**
  - Cumulative vs original baseline (77,389.46): **+77.8%**
- Re-profiled after the change: `Position.mark` / `PortfolioService.markToMarket` no
  longer appear in the top CPU or allocation samples. `BigDecimal.valueOf` allocation
  share dropped further from 14.8% (671/4520) to 10.6% (486/3039).
- Updated `docs/performance/OPTIMIZATIONS.md` (OPT-003 entry), `OPTIMIZATION_EVIDENCE.md`
  (raw per-run data and JFR findings), `BENCHMARKS.md` (post-OPT-003 numbers and the
  sustained-driver table), `FINAL_BENCHMARK_REPORT.md` (updated interpretation and
  honest verdict), `AGENT_CONTEXT.md`, and `TODO.md`.

**Verified:**
- `mvn test` — SUCCESS across all 16 modules.
- Differential check: identical order/trade counts (1,500,000 → 750,000) and no
  change in financial outcomes on the deterministic workload.

**Blockers:** None.

**Next session should:**
- Profile the new top `com.finex.*` CPU frames (`BinaryCodec.encodePayload`,
  `MatchingEngine.placeOrder`, `InMemoryLedger.post`, `SettlementService.settle`) and
  identify the next highest-value optimization. Likely candidate: event-log/ledger
  allocation reduction, `BigDecimal` hot-path cleanup, or metrics offloading. Always
  evidence first.

---

## 2026-09-03 — Session 11: OPT-002 — Evidence-driven optimization (market-data snapshot elimination)

**Scope:** Post-master-plan performance engineering pass, following a strict
measure-first methodology (reconnaissance → baseline → JFR profiling → hypothesis →
minimal change → benchmark → correctness verification → documentation).

**Done:**
- Reconnaissance: confirmed the existing tracking system (`PROJECT_PLAN.md`,
  `PROGRESS.md`, `TODO.md`, `AGENT_CONTEXT.md`, `DESIGN_DECISIONS.md`,
  `docs/performance/*`) and extended it rather than creating a new one.
- Established baseline: ran full `mvn test` (green), recorded hardware/software
  environment (Apple Silicon, 10 cores, 16 GB RAM, JDK 25.0.2).
- Built a reproducible, deterministic 1.5M-order sustained-load driver
  (`com.finex.benchmarks.SustainedSharedServiceDriver`) with bounded per-account
  cash/position (side flips every `accountCount` orders) for low-variance measurement.
- Profiled with JDK Flight Recorder (`settings=profile`): found
  `BookUpdateFactory.aggregate` (called unconditionally from
  `OrderService.publishBookUpdate`, even with zero market-data subscribers) was the
  **#1 CPU hotspot** and **56.8% of all sampled allocations**.
- **OPT-002:** added `MarketDataPublisher.hasSubscribers()` and guarded
  `OrderService.publishBookUpdate` / `publishMatchEvents` to skip snapshot/event
  construction entirely when nobody is subscribed. Zero behavior change when a
  subscriber exists (verified by new tests).
- Added regression tests: `MarketDataPublisherTest.hasSubscribersReflects...`,
  `OrderServiceMarketDataTest` (both "subscribed" and "no subscriber" cases).
- Measured before/after on the sustained driver (3 runs each): **+24% to +28%
  throughput** on the shared, no-subscriber `OrderService` path (77,389 → ~95,710-98,944
  ops/sec average). Re-profiled after the change: `BookUpdateFactory` no longer appears
  in CPU or allocation samples at all.
- Documented full methodology, raw data, and reproduction commands in
  `docs/performance/OPTIMIZATION_EVIDENCE.md`; wrote up the OPT-002 entry (with the
  Component/Problem/Evidence/Hypothesis/Change/Benchmark/Correctness/Decision schema)
  in `docs/performance/OPTIMIZATIONS.md`; cross-referenced from
  `docs/performance/FINAL_BENCHMARK_REPORT.md` and `BENCHMARKS.md`.
- Identified (but did not action) the next hotspot: `Position.mark` in
  `PortfolioService.markToMarket`, now the top CPU frame post-OPT-002. This is
  legitimate financial work, not wasted work, so it needs its own investigation
  before any change (candidate OPT-003).

**Verified:**
- `mvn test` — SUCCESS across all 16 modules (finex-api: 31/31 including 3 new tests;
  finex-benchmarks: new `SustainedSharedServiceDriverTest` passes).
- Differential check: identical order/trade counts (1,500,000 → 750,000) before and
  after the change on the same deterministic workload.

**Blockers:** None. Noted a local `exec-maven-plugin` quirk (documented in
`AGENT_CONTEXT.md`) where `-Dexec.mainClass` overrides are not honored on this
machine/plugin version; worked around by invoking `java -cp` directly.

**Next session should:**
- Investigate OPT-003 (`Position.mark` / `markToMarket` cost) with the same
  measure-first methodology, or continue down the priority list in the performance
  engineering master prompt (allocation reduction, fixed-point numerics, order-book
  structure, single-writer architecture) — always evidence first, one change at a time.

---

## 2026-09-03 — Session 10: Phases 23-24 — Documentation Consolidation and Final Benchmark Campaign

**Phase:** 23 → 24 (done)

**Done:**
- **Phase 23 — Documentation:**
  - Filled `docs/ARCHITECTURE.md` with module boundaries, data flow, concurrency model,
    security, and observability.
  - Filled `docs/PROTOCOL.md` with frame layout, primitive encodings, message types,
    and usage example.
  - Filled `docs/FINANCIAL_MODEL.md` with order lifecycle, risk limits, clearing,
    settlement, ledger, and portfolio.
  - Added ADR-010 (benchmarks/profiling), ADR-011 (observability), and ADR-012 (security)
    to `DESIGN_DECISIONS.md`.
  - Updated `README.md` with current status, API-key examples, metrics, and benchmark
    commands.
- **Phase 24 — Final Benchmark Campaign:**
  - Added `MultiThreadedLoadGeneratorBenchmark` (4 threads, isolated `OrderService` per
    invocation).
  - Re-ran the full JMH suite and captured order-level throughput numbers.
  - Wrote `docs/performance/FINAL_BENCHMARK_REPORT.md` with an honest assessment: pure
    matching reaches ~7M placements/sec, isolated end-to-end ~1M-2.7M orders/sec, but
    shared `OrderService` is ~53k/sec due to `BigDecimal`/`TreeMap`/synchronous
    settlement overhead. Included a concrete roadmap to a real 1M/sec shared engine.
  - Updated `docs/performance/BENCHMARKS.md` with the latest results.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS across all modules.

**Blockers:** None.

**Next session should:**
- Project is complete per the master plan. Any further work is follow-up optimization or
  deployment hardening.

---

## 2026-09-03 — Session 9: Phases 17-22 — Benchmarks, Profiling, Optimization, Observability, Failure Testing, Security

**Phase:** 17 → 22 (done)

**Done:**
- Updated `TODO.md`, `AGENT_CONTEXT.md`, and `PROGRESS.md`.
- **Phase 17 — Performance Benchmarks:** Added `finex-benchmarks` module with JMH and
  benchmarks for `OrderBook`, `MatchingEngine`, `OrderService`, and end-to-end `LoadGenerator`.
  Recorded baseline numbers in `docs/performance/BENCHMARKS.md`.
- **Phase 18 — Profiling:** Added `ProfileRunner` that captures a JFR recording around a
  `LoadGenerator` run and `docs/performance/PROFILING.md`.
- **Phase 19 — Optimization:** Implemented in-place `OrderBook.replaceOrder` and updated
  `MatchingEngine` to avoid TreeMap remove/re-insert on partially filled orders. Documented
  before/after numbers and analysis in `docs/performance/OPTIMIZATIONS.md`.
- **Phase 20 — Observability:** Added `MetricsService` with Micrometer counters/timer,
  wired `OrderService` to record submitted/rejected/cancelled orders, trades, and latency.
  Added Grafana dashboard JSON and provisioning under `docker/grafana/`.
- **Phase 21 — Failure Testing:** Added `OrderServiceFailureTest` and `EventStoreFailureTest`
  covering invalid input, risk rejections, order lifecycle failures, and corrupt event replay.
  Added `docs/testing/FAILURE_TESTING.md`.
- **Phase 22 — Security:** Added `ApiKey`, `ApiKeyService`, and `ApiKeyAuthenticationFilter`
  using the `X-API-Key` header. Enforced account isolation in `OrderController` (submit
  accountId match, get/cancel restricted to owner). Added `docs/security/API_KEYS.md`.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS across all modules.

**Blockers:** None.

**Next session should:**
- Phase 23 — Documentation review / consolidation, or Phase 24 — final benchmark campaign.

---

## 2026-09-02 — Session 8: Phases 12-16 — Portfolio, Clearing, Settlement, Replay, Load Generator

**Phase:** 11 → 12 → 13 → 14 → 15 → 16 (done)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md`, `README.md`, `DESIGN_DECISIONS.md`.
- **Phase 12 — Portfolio / P&L:** Added `finex-portfolio` module with `Position`, `Portfolio`,
  `PortfolioService`, and `PortfolioController` (`GET /api/v1/portfolios/{accountId}`).
  Tracks signed quantity, average price, realized/unrealized PnL, cash, and total equity.
- **Phase 13 — Clearing:** Added `finex-clearing` with `FeeSchedule`, `ClearingResult`, and
  `ClearingService` computing net buyer/seller cash and fee accrual.
- **Phase 14 — Settlement:** Added `finex-settlement` with `SettlementService` orchestrating
  clearing → ledger posting → portfolio update. `OrderService` delegates per-trade settlement.
- **Phase 15 — Replay:** Extended `OrderServiceReplayTest` to assert that a fresh `OrderService`
  replay produces identical order book, orders, ledger entries, and portfolios.
- **Phase 16 — Load Generator:** Added `finex-load-generator` module with `LoadConfig`,
  `LoadResult`, and `LoadGenerator` driving `OrderService` and reporting throughput/latency.
  `LoadGeneratorTest` validates deterministic shape and trades.
- Added ADR-008 (clearing/settlement) and ADR-009 (load generator) to `DESIGN_DECISIONS.md`.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS across all modules including the new `finex-portfolio`, `finex-clearing`,
  `finex-settlement`, `finex-load-generator`, and updated `finex-api` tests.

**Blockers:** None.

**Next session should:**
- Start Phase 17 — Performance Benchmarks (JMH/component/end-to-end) or any other priority.

---

## 2026-09-02 — Session 7: Phases 7-11 — Market Data, Binary Protocol, Event Log, Sharding, Ledger

**Phase:** 6 → 7 → 8 → 9 → 10 → 11 (done)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md`, `DESIGN_DECISIONS.md` for Phases 7-11.
- **Phase 7 — Market Data:** Added `finex-market-data` module with `MarketDataEvent` sealed
  hierarchy (`BookUpdate`, `TradeEvent`, `ExecutionEvent`), `PriceLevel`, `MarketDataPublisher`,
  `MarketDataListener`, and `SimpleMarketDataPublisher`. `OrderService` publishes trade/book
  events on every submit/cancel.
- **Phase 8 — Binary Protocol:** Added `finex-protocol` module with `ProtocolMessage` records
  and `BinaryCodec` using 4-byte length framing, single-byte enum ordinals, and UTF-8/BigDecimal
  string payloads. Round-trip tests for all message types.
- **Phase 9 — Event Architecture:** Added `finex-event-log` module with `Event`, `EventStore`,
  `InMemoryEventStore`, `CommandSerializer`, `CommandHandler`, and `ReplayEngine`.
  `OrderService` appends command events and is replayable; `OrderServiceReplayTest` verifies
  state reconstruction.
- **Phase 10 — Symbol Sharding:** Added `finex-shard` module with `SymbolShardRouter`,
  `EngineShard`, and `ShardCoordinator`. `OrderService` routes symbols to shards. Fixed
  `OrderService` order cache by extending `MatchResult` with `updatedOrders` populated by
  `MatchingEngine`.
- **Phase 11 — Ledger:** Added `finex-ledger` module with `Ledger`, `InMemoryLedger`,
  `LedgerAccount`, `LedgerEntry`, `DebitCredit`, and `AccountType`. `OrderService` posts a
  balanced cash leg and a balanced asset leg for every trade. `InMemoryLedger` enforces
  `sum(debits) == sum(credits)` per posting.
- Added `OrderServiceShardingTest`, `OrderServiceLedgerTest`, `SymbolShardRouterTest`,
  `InMemoryLedgerTest`, and updated `OrderServiceReplayTest`.
- Added ADR-006 (sharding) and ADR-007 (ledger) to `DESIGN_DECISIONS.md`.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS across all modules: `OrderControllerTest` (10),
  `OrderServiceReplayTest`, `OrderServiceShardingTest`, `OrderServiceLedgerTest`,
  `HealthControllerTest`, `MatchingEngineTest` (15), `OrderBookTest` (10), `InstrumentTest` (15),
  `RiskEngineTest` (10), `MarketDataPublisherTest` (2), `BinaryCodecTest` (7),
  `EventStoreTest` (2), `CommandSerializerTest` (2), `SymbolShardRouterTest` (3),
  `InMemoryLedgerTest` (4).

**Blockers:** None.

**Next session should:**
- Commit Phase 11.
- Start Phase 12 — Portfolio / P&L, Phase 13 — Clearing, Phase 14 — Settlement, or another
  priority.

---

## 2026-09-02 — Session 6: Phase 6 — Risk Engine

**Phase:** 5 → 6 (done)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md`, `README.md` for Phase 6.
- Added `finex-risk` Maven module and wired it into the parent reactor before `finex-api`;
  `finex-api` depends on `finex-risk`.
- Implemented baseline pre-trade risk engine in `finex-risk`:
  - `RiskConfig` with size, notional, position, cash exposure, collar, and rate-limit limits.
  - `AccountRiskState` tracking cash, position, cash/position reservations for open orders,
    and a sliding window of order timestamps.
  - `RiskResult` accepted/rejected with reason.
  - `RiskEngine` performing size, notional, collar, projected-position, cash-exposure,
    and rate-limit checks. Validates `LIMIT` and `MARKET` orders; reserves cash (BUY) and
    projected position (BUY/SELL) on acceptance.
  - `RiskEngineTest` (10 tests) covering acceptance, size, notional, cash, position,
    collar, market-without-last-trade, trade/cancel reservation lifecycle, and rate limit.
- Integrated `RiskEngine` into `finex-api` `OrderService`:
  - Per-symbol `lastTradePrice` map.
  - Per-account in-memory `AccountRiskState` map with default cash and position.
  - Pre-trade validation before `MatchingEngine.placeOrder`; rejects orders by throwing
    `OrderRejectedException` carrying a rejected `Order` and reason.
  - Updates buyer/seller cash and positions and releases reservations on every `Trade`.
  - Releases reservations on cancel.
- Added `Order.rejected(Instant)` and `OrderRejectedException`; `OrderResponse` now includes
  an optional `rejectionReason`.
- `GlobalExceptionHandler` returns `OrderResponse` with `status=REJECTED` for
  `OrderRejectedException`.
- Expanded `OrderControllerTest` to 10 tests, adding rejection cases for price collar,
  position limit, total open notional / cash exposure, and insufficient cash.
- Added ADR-005 documenting the in-memory, reservation-based risk-engine baseline.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `RiskEngineTest` 10/10, `OrderControllerTest` 10/10,
  `MatchingEngineTest` 15/15, `OrderBookTest` 10/10, `InstrumentTest` 15/15,
  `HealthControllerTest` 1/1.

**Blockers:** None.

**Next session should:**
- Commit Phase 6 work.
- Start Phase 7 — Market Data (BOOK_UPDATE/TRADE/EXECUTION events, snapshot + incremental,
  async publication) or choose another phase.

---

## 2026-09-02 — Session 5: Phase 5 — REST/API Layer

**Phase:** 4 → 5 (done)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md` for Phase 5.
- Added `finex-matching-engine` dependency to `finex-api/pom.xml` and managed it in parent
  `pom.xml`.
- Exposed `OrderBook.findOrder(long)` for accurate live order state lookup.
- Implemented `com.finex.api.order.OrderService`:
  - Per-symbol `MatchingEngine` map (auto-created on first order for a symbol).
  - Global `AtomicLong` order/sequence generator.
  - In-memory `Map<Long, Order>` cache with fallback to `OrderBook.findOrder`.
  - `submitOrder`, `cancelOrder`, `getOrder`, `getOrderBook`.
- DTOs in `finex-api`: `OrderRequest`, `OrderResponse`, `TradeView`, `OrderBookView`.
- `com.finex.api.order.OrderController`:
  - `POST /api/v1/orders` — submit and match
  - `DELETE /api/v1/orders/{orderId}` — cancel
  - `GET /api/v1/orders/{orderId}` — query
  - `GET /api/v1/order-books/{symbol}` — snapshot
- `com.finex.api.GlobalExceptionHandler` mapping `IllegalArgumentException` to 400.
- Validation in `OrderService` and `OrderController` for positive quantity, LIMIT price,
  MARKET price absence, and non-null enums/symbol.
- Added `spring-boot-starter-webmvc-test` dependency for Spring Boot 4 `WebMvcTest` and
  `MockMvc` support.
- `OrderControllerTest` (7 tests) using `@WebMvcTest`, `@Import` of `OrderService` and
  `GlobalExceptionHandler`, and `@DirtiesContext` to isolate `OrderService` state.
  Covered: submit and rest, full match with trade, query, cancel, book snapshot,
  validation rejection.
- Updated `README.md` with curl examples for the trading API.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `OrderControllerTest` 7/7, `MatchingEngineTest` 15/15,
  `OrderBookTest` 10/10, `InstrumentTest` 15/15, `HealthControllerTest` 1/1.

**Blockers:** None.

**Next session should:**
- Commit Phase 5 work.
- Start Phase 6 — Risk Engine or choose another phase (Market Data, Binary Protocol, Event
  Architecture, etc.).

---

## 2026-09-02 — Session 4: Phase 4 — Matching Engine

**Phase:** 3 → 4 (in progress)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md` for Phase 4.
- Added `finex-matching-engine` Maven module, wired into parent `pom.xml` between
  `finex-order-book` and `finex-api`.
- Implemented `com.finex.matching.MatchingEngine`:
  - Per-symbol, single-threaded baseline.
  - `placeOrder(Order, Instant)` returns `MatchResult` (final order, trades, addedToBook).
  - Walks opposite side of book from top, matching at resting order's price.
  - Full and partial fills for both incoming and resting orders using `Order.withFill`.
  - `Trade` generation with monotonic `tradeSequence` from an internal `AtomicLong`.
  - Limit price gating; market orders fill until liquidity is exhausted, then cancel
    the unfilled remainder.
  - `cancelOrder(long)` delegates to `OrderBook`.
- Added `MatchingEngineTest` (15 tests) covering full fill, partial incoming fill,
  partial resting fill, multiple fills across price levels, market order full fill and
  cancellation, non-marketable limit order resting, buy/sell price gating, same-price
  time priority, cancellation, wrong-symbol rejection, determinism, and resting-order
  re-insertion priority.
- ADR-004: matching engine architecture (single-symbol, single-threaded baseline, caller-
  supplied `Instant` for determinism).

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `MatchingEngineTest` 15/15, `OrderBookTest` 10/10,
  `InstrumentTest` 15/15, `HealthControllerTest` 1/1.

**Blockers:** None.

**Next session should:**
- Commit Phase 4 work.
- Start Phase 5 — REST/API Layer: expose `POST /api/v1/orders`, `DELETE /api/v1/orders/{id}`,
  `GET /api/v1/orders/{id}` over `finex-api`, backed by `MatchingEngine` or a service
  orchestrator. Need to decide whether to wire the engine directly or introduce an
  `OrderService` / `Gateway` abstraction.

---

## 2026-09-02 — Session 3: Phase 3 — Correct Order Book

**Phase:** 2 → 3 (in progress)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md` for Phase 3.
- Added `finex-order-book` Maven module, wired into parent `pom.xml`.
- Implemented `com.finex.orderbook.OrderBook`:
  - `TreeMap<BigDecimal, List<Order>>` for bids (descending price) and asks (ascending).
  - Per-price lists sorted by `sequence` for time priority.
  - `ConcurrentHashMap<Long, Order>` for fast id lookup on cancellation.
  - `addOrder`, `cancelOrder`, `bestBid`, `bestAsk`, `getBids`, `getAsks`.
  - Validation: only LIMIT orders, positive remaining quantity, resting statuses.
- Added `OrderBookTest` (10 tests) covering: empty book, best bid/ask, time priority at
  same price, cancellation, price level removal, rejection of non-LIMIT/wrong-symbol
  orders, repeated identical inputs determinism, and flat bid/ask views.
- ADR-003: TreeMap + per-price list baseline; measure alternatives before optimizing.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `OrderBookTest` 10/10, `InstrumentTest` 15/15,
  `HealthControllerTest` 1/1.

**Blockers:** None.

**Next session should:**
- Commit Phase 3 work.
- Start Phase 4 — Matching Engine: deterministic price-time-priority matching, full/partial
  fills, `Trade` generation, `Order` state updates. Matching engine likely becomes
  `finex-matching-engine` module or extends `finex-order-book`; decide as Phase 4 begins.

---

## 2026-09-02 — Session 2: Phase 2 — Financial Domain Model

**Phase:** 1 → 2 (in progress)

**Done:**
- Started Phase 2; updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md`.
- Added framework-agnostic domain model to `finex-common`:
  - Enums: `Side`, `OrderType`, `OrderStatus`, `InstrumentStatus`, `AccountStatus`,
    `DebitCredit`, `EntryType`, `LedgerAccountType`.
  - Records: `User`, `Account`, `Instrument`, `Order`, `Trade`, `Position`, `Balance`,
    `LedgerAccount`, `LedgerEntry`.
- Domain validation in constructors: positive prices/quantities/tick/lot sizes,
  non-negative balances/positions, blank string guards, non-null references.
- `Order` is immutable and provides `withFill(...)` / `cancelled(...)` copy methods to
  support fill/cancel state transitions without mutating the original.
- `Position.marketValue(...)` and `Position.totalPnl()` helper methods.
- `Balance.total()` helper.
- `InstrumentTest`: 15 unit tests covering construction, basic invariants, order fill
  transitions, trade creation, balance/position math, and ledger entry validation.
- ADR-002: `BigDecimal` for fixed-point money/quantity in the baseline (no custom
  wrappers yet; will measure before optimizing the hot path).
- Added AssertJ as a test dependency to `finex-common/pom.xml`.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `finex-common` 15/15 domain tests pass,
  `finex-api` `HealthControllerTest` still passes (1/1).

**Blockers:** None.

**Next session should:**
- Commit Phase 2 work.
- Start Phase 3 — Correct Order Book (TreeMap-backed, price-time priority) or continue
  expanding Phase 2 if additional entities (e.g. `ExecutionReport`) are needed.

---

## 2026-09-02 — Session 1: Project bootstrap

**Phase:** 0 → 1 (in progress)

**Done:**
- Read Master Plan (`../Master Plan.md`), extracted phased roadmap into `PROJECT_PLAN.md`.
- Decided: Maven multi-module build, Docker Compose for infra (Postgres/Prometheus/Grafana).
- `git init`, `.gitignore` created.
- Project memory files created: PROJECT_PLAN.md, PROGRESS.md (this file), TODO.md,
  AGENT_CONTEXT.md, DESIGN_DECISIONS.md.
- Environment verified: Java 25.0.2, Maven 3.9.16, Docker 29.4.2, Docker Compose v5.1.3.

- Scaffolded Maven parent POM (Java 25, Spring Boot 4.1.1 BOM) + `finex-common` (empty
  shared module) + `finex-api` (Spring Boot app) modules.
- `docker-compose.yml`: postgres (16-alpine), prometheus (v3.13.2), grafana (13.0.7),
  with Prometheus scrape config and a provisioned Grafana datasource under `docker/`.
- `finex-api`: `GET /api/v1/health` (checks DataSource connectivity), Flyway migration
  `V1__init.sql` (placeholder bootstrap marker table), `application.yml` reading DB
  connection from env vars with localhost defaults, actuator + prometheus endpoint exposed.
- Test: `HealthControllerTest` — Testcontainers-backed Postgres, full Spring context,
  asserts `/api/v1/health` returns 200 with `db: UP`. (Note: JUnit test classes must be
  named `*Test`/`Test*`, not `*IT`, for the default Surefire include pattern to pick them
  up — no Failsafe plugin configured yet.)
- `docs/` skeleton created (ARCHITECTURE.md, PROTOCOL.md, FINANCIAL_MODEL.md,
  performance/{BENCHMARKS,EXPERIMENTS,OPTIMIZATIONS}.md) — placeholders only.
- README.md with build/run/test instructions.
- ADR-000 (Maven) and ADR-001 (Docker infra, native app for now) recorded.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS (all modules).
- `mvn test` — SUCCESS, 1 test, real Testcontainers Postgres + Flyway + Spring context.
- `docker compose up -d postgres` (note: local machine's default 5432 was already bound by
  an unrelated container, used `DB_PORT=5442` override) + `mvn -pl finex-api
  spring-boot:run` + `curl localhost:8080/api/v1/health` → `{"status":"UP","db":"UP"}`.
- Stopped app process and `docker compose down` afterward; environment left clean.

**Blockers:** None. Note for future sessions: port 5432 may be occupied by unrelated local
Docker containers on this machine — pass `DB_PORT=<free-port>` to `docker compose up` if so.

**Next session should:**
- `git add -A && git commit` this bootstrap (not yet committed as of writing this entry).
- Start Phase 2 (Financial Domain Model): expand atomic tasks in TODO.md, then implement.
