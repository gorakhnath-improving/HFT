# Design Decisions (ADRs)

Format: Context / Options / Decision / Reason / Tradeoffs / Consequences.

---

## ADR-000: Build tool — Maven

**Context:** Master Plan allows Maven or Gradle for the multi-module Java/Spring Boot
project.

**Options:**
- Gradle (Kotlin DSL): faster incremental builds, common in perf-sensitive Java projects.
- Maven: ubiquitous in enterprise Spring Boot, verbose XML, stable multi-module conventions.

**Decision:** Maven.

**Reason:** Explicit user preference; also plays well with Spring Boot's default tooling
and most engineers' familiarity, reducing onboarding friction for a portfolio project
reviewers will clone and build.

**Tradeoffs:** Slower incremental builds than Gradle; more verbose module wiring.

**Consequences:** Multi-module reactor with a parent `pom.xml` managing dependency/plugin
versions (Spring Boot BOM, Testcontainers BOM, etc.). New modules added as new
`<module>` entries as phases progress.

---

## ADR-001: Infrastructure via Docker Compose, app runs natively (for now)

**Context:** Master Plan requires Docker/Docker Compose for infrastructure, and the app
needs Postgres (later Prometheus/Grafana, possibly Kafka-like queues) during development.

**Options:**
- Containerize everything (app + infra) via Docker Compose from day one.
- Run only infra components (Postgres, Prometheus, Grafana) in Docker; run the Spring
  Boot app natively via Maven during development for fast iteration/debugging.

**Decision:** Infra in Docker Compose; app runs natively via `mvn spring-boot:run` during
development. A Dockerfile for the app itself will be added later (Phase 20+/23) once the
service boundaries stabilize, so CI/deployment can containerize it too.

**Reason:** Fast local iteration (no image rebuild per code change) while still getting
reproducible, disposable infra dependencies as requested.

**Tradeoffs:** Local dev environment isn't 100% identical to a fully-containerized
deployment until the app Dockerfile is added.

**Consequences:** `docker-compose.yml` at repo root defines `postgres`, `prometheus`,
`grafana`. `finex-api/src/main/resources/application.yml` reads DB connection info from
environment variables with sensible localhost defaults.

---

## ADR-002: Money / fixed-point numeric representation — `java.math.BigDecimal`

**Context:** FinEx handles prices, quantities, P&L, and ledger amounts. Master Plan §17
prohibits blind use of floating-point arithmetic for money. Phase 2 requires a
representation for the domain model that is exact, deterministic, and reviewable.

**Options:**
- `double` / `float`: fast, but inexact and unsuitable for financial calculations
  (violates Master Plan §17).
- `long` with an implicit fixed scale (e.g. cents): fast and exact, but leaks the scale
  convention into every calculation and is error-prone.
- Custom `Money` / `Quantity` wrapper classes: explicit, but adds API overhead and
  requires defining arithmetic methods before we have any benchmark evidence they are
  needed on the hot path.
- `java.math.BigDecimal`: standard, arbitrary-precision decimal, exact, deterministic,
  understood by financial developers, and works well for the baseline domain model.

**Decision:** Use `BigDecimal` directly in the baseline domain model. No custom money
wrapper yet. Precision/scale validation is done at object construction; calculations are
carried out with `BigDecimal.ONE` / `add` / `subtract` / `multiply` and exact arithmetic
where possible. If later profiling shows allocation or arithmetic cost on the HFT hot
path (matching engine), we may introduce `long` fixed-point or a specialized immutable
wrapper and record that in ADR-00X.

**Reason:** Phase 2 is correctness-first. `BigDecimal` gives exact decimal arithmetic
without scale leakage, satisfies the Master Plan requirement, and is easy for reviewers
to understand. Prematurely adding a custom wrapper before we have a hot path to optimize
would be over-engineering.

**Tradeoffs:** `BigDecimal` creates more objects than primitive `long`s and has higher
arithmetic cost than fixed-point. It is not ideal for the eventual 1M orders/sec hot
path, but that is not the current concern (Master Plan §50: "Correctness → Tests →
Observability → Benchmark → Profile → Optimize").

**Consequences:**
- All price, quantity, P&L, and balance fields in `finex-common` are `BigDecimal`.
- Constructors validate positivity / non-negativity of numeric values.
- No `double`/`float` is used for money anywhere in the codebase; this is enforced by
  code review and later by static analysis if needed.
- Future optimization will be measured before it is applied (JMH/component benchmarks).

---

## ADR-003: Order book data structure — TreeMap + per-price list baseline

**Context:** Phase 3 needs a simple, correct, deterministic order book. Master Plan §9
lists many possible structures (TreeMap, sorted arrays, primitive collections, custom
price-level structures, intrusive linked lists, radix structures) and explicitly states
"Do not assume a theoretically faster data structure is actually faster. Measure." This
means the first implementation should be a measured baseline, not an over-engineered hot
path.

**Options:**
- `TreeMap<BigDecimal, List<Order>>` with explicit bid/ask comparators: very simple,
  correct, easy to test, built into the JDK, but allocates `Order` objects on inserts.
- Sorted array / primitive array per price level: faster lookup/insert for small levels,
  but harder to maintain and requires custom sorting logic.
- Custom price-level object with intrusive doubly-linked list: ideal for HFT, but complex
  and error-prone; needs extensive testing before trusting.
- Agrona / Eclipse Collections primitive collections: less GC pressure, but adds a
  dependency and requires conversion from domain objects.

**Decision:** Start with `TreeMap<BigDecimal, List<Order>>` in a new `finex-order-book`
module. Each side has its own sorted map. Within a price level, a plain `ArrayList` keeps
orders in `sequence` (time) order. A `ConcurrentHashMap<Long, Order>` provides fast
id-lookup for cancellation. This is the baseline (V1/V2) structure.

**Reason:** It is the smallest structure that guarantees correct price-time priority and
determinism. It also matches the Master Plan's "Correctness → Tests → Benchmark → Profile
→ Optimize" ordering. We need a working, tested baseline before we can measure whether a
fancier structure is actually faster.

**Tradeoffs:**
- `BigDecimal` tree comparisons and `Order` allocations are not ideal for the 1M orders/sec
  target, but the hot-path optimization comes later and will be measured.
- `ArrayList` insertion in the middle (out-of-sequence orders) is O(n), but in the common
  case of strictly increasing `sequence` it is a single append.
- `ConcurrentHashMap` adds some overhead; the baseline is not yet lock-free, but no shared
  mutable state is exposed beyond the internal maps.

**Consequences:**
- `OrderBook` lives in `finex-order-book` and depends only on `finex-common`.
- Price-time priority for BUY (highest first, then earliest sequence) and SELL (lowest
  first, then earliest sequence) is implemented and unit tested.
- Determinism is easy to prove because the data structure is deterministic and `Order`
  is immutable.
- When the matching engine and load generator are ready, JMH or component benchmarks will
  compare this baseline against alternative book implementations. Results will be recorded
  in `docs/performance/EXPERIMENTS.md`.

---

## ADR-004: Matching engine architecture — single-symbol, single-threaded baseline

**Context:** Phase 4 requires a deterministic matching engine that applies price-time
priority to incoming orders. Master Plan §8 specifies full fills, partial fills, multiple
fills, market orders, cancellation, and order replacement. The engine must produce the
same execution sequence for the same command sequence.

**Options:**
- Single `MatchingEngine` instance per symbol, single-threaded, directly mutating an
  `OrderBook`: simplest to reason about, deterministic, but not yet sharded or lock-free.
- Multi-symbol engine with internal sharding: needed later (Phase 10) but premature for a
  baseline.
- Lock-free/disruptor-based engine: maximally performant, but requires proving
  single-threaded correctness first and is overkill for the current phase.

**Decision:** Implement a per-symbol `MatchingEngine` that owns an `OrderBook` and runs
single-threaded in the baseline. It exposes `placeOrder(Order, Instant)` returning a
`MatchResult` (final order state, list of trades, whether the order was added to the book)
and `cancelOrder(long)` delegating to the book. The caller supplies the timestamp, making
tests fully deterministic.

**Reason:** This is the smallest correct design that satisfies the Master Plan's matching
requirements and preserves determinism. It separates the matching logic from the order
book data structure, making it possible to benchmark and replace either component later.
Concurrency and symbol sharding are explicit future phases (10, 25).

**Tradeoffs:**
- A caller (e.g. gateway/router) must serialize commands per symbol. Parallelism will be
  introduced later via symbol sharding, not by making `MatchingEngine` internally concurrent.
- The engine currently cancels and re-adds a partially filled resting order, which is
  more work than an in-place update. That is acceptable for the baseline and can be
  optimized later with a dedicated `OrderBook.replace` method if benchmarks justify it.

**Consequences:**
- `finex-matching-engine` module depends on `finex-order-book` and `finex-common`.
- `MatchingEngine` is deterministic and covered by 15 unit tests including full/partial
  fills, market orders, limit price gating, same-price time priority, and repeated-run
  determinism.
- `Trade.tradeSequence` is generated by an internal `AtomicLong`, so the same command
  sequence always produces the same trade sequence numbers.
- The engine does not yet handle `MODIFY`/`Cancel-replace` explicitly; cancellation plus a
  new order will be used until a dedicated modify path is needed.
- Event sourcing / replay infrastructure (Phase 9/15) can record `placeOrder` commands and
  `MatchResult` outputs and replay them to reconstruct state.

---

## ADR-005: Risk engine — in-memory, per-account, reservation-based baseline

**Context:** Phase 6 requires pre-trade risk checks (size, notional, collar, position,
exposure, rate limit) before an order reaches the matching engine. The system does not yet
have a ledger or settlement service (Phase 11), so the risk engine must maintain enough
account state to enforce these limits without full accounting.

**Options:**
- Validate only the current order with static config, ignoring cross-order/account state:
  simple, but allows multiple resting orders to collectively exceed cash or position limits.
- Maintain per-account in-memory `AccountRiskState` with cash/position and reservations for
  open orders, updating on trades and cancels: correct for a single node, more complex.
- Integrate a full ledger/portfolio service now: premature; ledger and positions are later
  phases and would couple risk to persistence.

**Decision:** Implement `finex-risk` with a stateless `RiskEngine` that operates on a mutable
`AccountRiskState` owned by `OrderService`. Accepted orders reserve cash (BUY) and projected
position (BUY/SELL); trades reduce reservations and update cash/position; cancellations
release remaining reservations. All state is in-memory in the baseline.

**Reason:** This is the smallest design that prevents cross-order limit violations (e.g.
cash double-spend across multiple open buy orders or position over-extension across sells)
without building a full ledger. The `RiskEngine` is deterministic and testable in isolation;
`OrderService` owns the lifecycle and state maps.

**Tradeoffs:**
- In-memory state is lost on restart. Event sourcing/replay (Phase 9) and persistence
  (Phase 11) will eventually make this durable.
- `AccountRiskState` uses `BigDecimal` maps; this is not optimized for the HFT hot path but
  is correct for the baseline.
- Market-order risk uses the last trade price as a notional estimate; a more conservative
  approach (best ask/bid) can be added when order-book depth is exposed to risk.

**Consequences:**
- New `finex-risk` module between `finex-matching-engine` and `finex-api`.
- `OrderService` calls `RiskEngine.validate(...)` before `MatchingEngine.placeOrder(...)` and
  throws `OrderRejectedException` on rejection, producing an `OrderResponse` with
  `status=REJECTED`.
- `OrderService` tracks `lastTradePrice` per symbol and updates `AccountRiskState` on each
  `Trade` and cancel.
- Default config and initial cash are hard-coded for the baseline; they will move to
  configuration/account profiles once the account service is built.

---

## ADR-006: Symbol sharding — static shard count with hash-based routing

**Context:** Phase 10 requires multiple independent matching-engine shards so symbols can
be processed concurrently as the system scales.

**Options:**
- One `MatchingEngine` per symbol, all running on a single thread: simple but no parallelism.
- Assign symbols to a fixed number of `EngineShard` instances using `hashCode` modulo: easy to
  implement, deterministic, and scales horizontally by shard count; changing shard count
  reshuffles symbols.
- Consistent hashing with virtual nodes: better redistribution when shard count changes, but
  more complex and premature for a single-node baseline.
- Thread-per-shard with work queues: needed for true parallelism but is Phase 25 (concurrency
  model); we want the routing abstraction now without committing to an executor design.

**Decision:** Introduce `finex-shard` with `SymbolShardRouter`, `EngineShard`, and
`ShardCoordinator`. Each `EngineShard` owns per-symbol `MatchingEngine` instances.
`OrderService` routes a command to `coordinator.shardFor(symbol)` and invokes the shard.
The default shard count is `1`; tests and future config can raise it without changing the
service code.

**Reason:** This gives a clean routing boundary that can later be backed by a thread pool,
process, or host per shard. Deterministic hash routing is sufficient for the baseline and
keeps shard placement testable.

**Tradeoffs:**
- Re-sharding when `shardCount` changes requires replaying events; this is acceptable for a
  single-node baseline.
- `EngineShard` is still called on the caller's thread; concurrency is an explicit future phase.
- Cross-symbol aggregation (e.g. account cash across shards) remains global in `OrderService`.

**Consequences:**
- `finex-shard` is a dependency of `finex-api`.
- `OrderService` no longer holds a `Map<String, MatchingEngine>` directly; it delegates to
  `ShardCoordinator`.
- `OrderService` exposes `replay()` and `eventStore()` to support replay across shards.
- `MatchingEngine` returns `MatchResult.updatedOrders` so `OrderService` can keep its order
  cache correct regardless of which shard produced a fill.

---

## ADR-007: Ledger — in-memory double-entry postings per trade

**Context:** Phase 11 requires a double-entry ledger with immutable entries and an invariant
that debits equal credits for every posting.

**Options:**
- Build a full general ledger with chart of accounts, journals, and ledgers: overkill for the
  baseline and duplicates future clearing/settlement phases.
- Post simple, balanced entries for each trade leg: one cash posting and one asset posting
  per trade, each with a debit and a credit. This satisfies the double-entry invariant, is
  easy to test, and integrates cleanly with `OrderService`.
- Wait for a dedicated settlement/clearing service: would delay the ledger and leave the
  system without a baseline accounting record.

**Decision:** Add `finex-ledger` with `Ledger`, `InMemoryLedger`, `LedgerAccount`, `LedgerEntry`,
and `DebitCredit`. `OrderService` posts a cash leg (seller debit cash, buyer credit cash) and
an asset leg (buyer debit asset, seller credit asset) for every `Trade`.

**Reason:** This is the smallest design that satisfies the double-entry invariant and gives
future Portfolio / P&L (Phase 12) and Clearing (Phase 13) a concrete ledger to query. Keeping
it in memory matches the current baseline; persistence will be added when settlement matures.

**Tradeoffs:**
- Ledger state is not replayed from the event log yet; replay reconstructs matching and risk,
  not ledger. A future ledger event log can be added when needed.
- Asset postings use the symbol as the currency/unit (e.g. `BTC-USD` quantity), while cash
  postings use `USD`; the two are separate balanced postings, not a single four-line journal.
  This is acceptable for the baseline but may be unified later.
- No account creation validation yet; accounts are created implicitly on first use.

**Consequences:**
- `finex-ledger` is a dependency of `finex-api`.
- Every trade creates a cash posting (3 entries when fees are present) and an asset posting
  (2 entries).
- `InMemoryLedger` rejects unbalanced postings and never mutates existing entries.
- `OrderService.ledger()` exposes the ledger for tests and external queries; replay now
  reconstructs an identical ledger.

---

## ADR-008: Clearing and settlement — separate modules with a single orchestrator

**Context:** Phases 13 and 14 require trade clearing (buyer/seller obligations and fees) and
settlement (cash/asset movement, ledger finalization, and portfolio update). The modules need
a clear responsibility split without fragmenting the per-trade flow.

**Options:**
- Put clearing, settlement, portfolio, and ledger logic all inside `OrderService`: simplest for
  small scale but couples every concern and makes testing/reuse hard.
- Split into separate modules but let `OrderService` call each one individually: clearer, but the
  caller still owns the correct ordering of clearing → ledger → portfolio.
- Add a `SettlementService` in a dedicated `finex-settlement` module that owns the whole
  post-trade lifecycle; `OrderService` makes one call per trade.

**Decision:** Create `finex-clearing` for fee schedule and obligation math, and `finex-settlement`
as the orchestrator that uses `ClearingService`, `Ledger`, and `PortfolioService`.
`OrderService` calls `settlementService.settle(trade, takerSide, now, markPrice)`.

**Reason:** This keeps each module focused and testable in isolation. `OrderService` stays a
coordinator of matching, risk, market data, events, and settlement rather than a growing blob
of accounting logic. The fee/taker math lives in `ClearingService`, which can later be replaced
by a real clearing engine.

**Tradeoffs:**
- Additional module wiring and one more object allocation per trade (the `ClearingResult`).
- `OrderService` still passes `command.side()` as the taker side because the matching engine
  does not yet record aggressor on the `Trade`. This is an acceptable leak until `Trade` is
  extended.

**Consequences:**
- `finex-clearing` and `finex-settlement` are dependencies of `finex-api`.
- A trade produces balanced ledger entries including a `FEE.ACCRUAL` debit.
- `PortfolioService` cash reflects net clearing deltas (notional ± fees).
- `SettlementServiceTest` verifies ledger + portfolio state for a single trade.

---

## ADR-009: Load generator — deterministic, single-threaded harness

**Context:** Phase 16 needs a dedicated Java load generator that can exercise the exchange with
configurable workloads before building full JMH/component benchmarks (Phase 17).

**Options:**
- Use an external tool (e.g. JMeter, k6, wrk): can hit the HTTP API but does not exercise the
  internal `OrderService` directly and adds infrastructure.
- Write a small Java harness inside `finex-api` tests: quick, but not reusable as a CLI entry
  point.
- Create a separate `finex-load-generator` module that depends on `finex-api` and drives
  `OrderService` directly: reusable, testable, and can evolve into a real benchmark driver.

**Decision:** Add `finex-load-generator` with `LoadConfig`, `LoadResult`, and `LoadGenerator`.
The generator submits orders deterministically and reports throughput, average latency, and
maximum latency. It intentionally runs single-threaded on the caller thread to establish a
baseline latency distribution.

**Reason:** A separate module keeps load generation out of the production API while still using
the real `OrderService` and matching stack. Deterministic pricing (alternating sells below and
buys above the base price) guarantees trades and leaves book depth for later assertions.

**Tradeoffs:**
- Single-threaded throughput is far below the eventual 1M orders/sec target; it is a functional
  harness, not a final benchmark.
- Wall-clock latency includes JVM warm-up and GC; results are for shape/relative comparison, not
  absolute performance claims.
- Depending on `finex-api` pulls in Spring Boot and Testcontainers dependencies; for a dedicated
  load runner this is acceptable for now and can be trimmed later.

**Consequences:**
- `finex-load-generator` is part of the reactor and runs `LoadGeneratorTest` in `mvn test`.
- `LoadResult` is serializable and suitable for future benchmark reporting.
- A future CLI main class can call `LoadGenerator.run(config)` directly.

---

## ADR-010: Benchmarking and profiling harness — JMH + JFR

**Context:** Phases 17-18 need reproducible benchmarks and profiling to guide optimization.

**Options:**
- Hand-written timing loops: quick but prone to JVM warmup/GC bias.
- JMH: the standard JVM benchmark harness; handles warmup, dead-code elimination, and
  blackholes.
- External load tools (k6, JMeter): exercise HTTP but not internal hot path classes.

**Decision:** Add a dedicated `finex-benchmarks` module with JMH for micro and component
benchmarks, plus a `ProfileRunner` that wraps a `LoadGenerator` run with JDK Flight Recorder.

**Reason:** JMH gives trustworthy per-component numbers; `ProfileRunner` gives a full-call-stack
view of the end-to-end workload. Both are invoked from Maven/CLI so they are easy to repeat.

**Tradeoffs:**
- JMH adds an annotation processor and requires tuning annotation paths.
- Forked runs are more reliable but harder to run via `exec:java`; the baseline uses
  same-JVM runs for convenience.
- End-to-end `LoadGenerator` benchmarks allocate a fresh `OrderService` per invocation, so
  they include construction cost.

**Consequences:**
- `finex-benchmarks` depends on core modules and `finex-load-generator`.
- Baseline numbers live in `docs/performance/BENCHMARKS.md`; optimization rationale lives in
  `docs/performance/OPTIMIZATIONS.md`.
- `ProfileRunner` output is a `.jfr` file that can be opened in JDK Mission Control.

---

## ADR-011: Observability — Micrometer + Prometheus

**Context:** Phase 20 needs runtime metrics for throughput, rejections, and latency.

**Options:**
- Hand-rolled counters and an HTTP `/metrics` endpoint.
- Dropwizard Metrics.
- Micrometer with Prometheus registry.

**Decision:** Use Micrometer with `micrometer-registry-prometheus` because it integrates
with Spring Boot actuator and is the de-facto standard for JVM metrics.

**Reason:** Minimal integration work, standardized metric format, and easy Grafana consumption.

**Tradeoffs:**
- `Timer` uses time buckets; for nanosecond-level matching latency aHdrHistogram or custom
  ring buffer may be needed later.
- Metrics add per-call overhead; in the HFT hot path they would likely be sampled or moved
  off the critical thread.

**Consequences:**
- Custom counters under `finex.orders.*` and `finex.trades` plus `finex.order.latency`.
- `/actuator/prometheus` is exposed for Prometheus scraping.
- Grafana dashboard JSON is provisioned in `docker/grafana/dashboards/`.

---

## ADR-012: Security — API-key authentication and account isolation

**Context:** Phase 22 requires authentication and authorization for trading endpoints.

**Options:**
- Spring Security with JWT/OAuth2: industry standard but heavy for a baseline.
- Servlet filter validating an `X-API-Key` header and mapping it to an account.

**Decision:** Implement a lightweight `OncePerRequestFilter` plus `ApiKeyService`.

**Reason:** It satisfies the requirement with no new dependencies beyond Spring Web and is
easy to understand and test. A real deployment can replace or wrap it with Spring Security.

**Tradeoffs:**
- No token expiry/rotation; keys are in-memory.
- No role-based access control; a single key maps to one account.

**Consequences:**
- `/api/v1/orders/**` requires `X-API-Key`.
- `OrderController` rejects requests whose `accountId` does not match the key's account and
  filters get/cancel by owner.
- `OrderResponse` now includes `accountId` to support isolation checks.
