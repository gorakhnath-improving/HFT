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
