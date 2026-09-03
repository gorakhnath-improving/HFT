# Optimizations

> Recorded starting Phase 19, including failed optimizations with useful engineering
> insight (Master Plan §35). Each entry follows: Component / Problem / Evidence /
> Hypothesis / Change / Before / After / Delta / Correctness / Decision / Status.
> All numbers are measured on this machine (Apple Silicon macOS, JDK 25.0.2, 10 cores,
> 16 GB RAM) and are not portable performance claims.

## OPT-001 (Phase 19) — In-place resting-order updates

### Hypothesis

`MatchingEngine` was removing and re-adding a partially filled resting order on every fill.
For a single price level this means a `TreeMap` remove/insert plus an `ArrayList` remove and
sorted insertion. Replacing the order reference in-place should reduce that work.

### Change

- Added `OrderBook.replaceOrder(long orderId, Order newOrder)` to update a partially filled
  order without a TreeMap remove/insert.
- Updated `MatchingEngine` to call `replaceOrder` when the resting order still has remaining
  quantity, and `cancelOrder` only when it is fully filled.

### Results

Measured with `finex-benchmarks` `BenchmarkRunner` (single-JVM, no fork):

| Benchmark | Before (ops/s) | After (ops/s) | Delta |
|-----------|---------------:|--------------:|------:|
| `MatchingEngineBenchmark.placeBuyAndSell` | 3,655,769.3 | 3,713,991.7 | +1.6% |
| `OrderBookBenchmark.addAndCancel` | 15,067,286.9 | 14,607,902.8 | -3.0% (noise) |
| `OrderServiceBenchmark.submitLimitOrder` | 55,762.9 | 58,178.3 | +4.3% |

### Analysis

- The `MatchingEngine` improvement is small and within normal JMH variance on this machine.
- The dominant cost is still `BigDecimal` allocation and `TreeMap` navigation, not the
  remove/re-insert of the price level.
- This validates ADR-003: the TreeMap baseline is acceptable for correctness, but a 1M
  orders/sec target will require replacing `BigDecimal`/`TreeMap` with fixed-point primitive
  structures later.

### Conclusion

The in-place update is a correctness-preserving cleanup and removes unnecessary work, so it
is kept. It is not a breakthrough optimization. The next evidence-driven optimization cycle
(Phase 24) should benchmark alternative book structures (`Long2ObjectOpenHashMap`, sorted
arrays, intrusive lists) and fixed-point price representation.

**Status:** COMPLETED / KEPT.

---

## OPT-002 — Skip market-data snapshot construction when there are no subscribers

**Component:** `finex-api` `OrderService` (`publishBookUpdate`, `publishMatchEvents`),
`finex-market-data` `MarketDataPublisher` / `SimpleMarketDataPublisher`.

**Problem:** `OrderService.publishBookUpdate` unconditionally rebuilt a full `BookUpdate`
snapshot on *every single order submission* — regardless of whether anything was actually
subscribed to market data. Building that snapshot means:
1. `OrderBook.getBids()` / `getAsks()` copy every resting order on both sides into new
   `ArrayList`s (`flatView`).
2. `BookUpdateFactory.aggregate()` then walks that list and calls `BigDecimal.add()`
   repeatedly to build per-price-level totals, allocating a new `PriceLevel` per level.

This work was thrown away immediately whenever `SimpleMarketDataPublisher` had zero
listeners — which is the case in every benchmark, the load generator, and any real
deployment where nobody has subscribed yet.

**Evidence:** JFR profiling (JDK Flight Recorder, `settings=profile`) of an ad hoc
1.5M-order driver (500 accounts, alternating buy/sell blocks to keep cash/position
bounded, single shared `OrderService`, no market-data subscriber) taken *before* the
change:

- CPU (`jdk.ExecutionSample`, leaf frame): `BookUpdateFactory.aggregate` was the #1
  hotspot at 46/1389 sampled leaf frames (3.3%) — more than triple the next highest
  `com.finex.*` frame (`OrderBook.getAsks`, 15/1389).
- Allocation (`jdk.ObjectAllocationSample`, leaf frame): `java.math.BigDecimal.valueOf`
  was **60.7%** of all sampled allocations (3288/5420), and **3077 of those 3288**
  (93.6% of the `BigDecimal.valueOf` samples, 56.8% of *all* sampled allocations) were
  attributed directly to `BookUpdateFactory.aggregate(List)` line 38
  (`currentQty = currentQty.add(qty)`).

Raw recordings: `/tmp/finex-baseline.jfr` (before), `/tmp/finex-after.jfr` (after) —
not committed (binary JFR files are not checked into the repository).

**Hypothesis:** Guarding both `publishBookUpdate` and `publishMatchEvents` with a cheap
`publisher.hasSubscribers()` check (backed by `!listeners.isEmpty()` on the existing
`CopyOnWriteArrayList`) eliminates all of that wasted work with **zero behavior change**
when a subscriber is actually present, since the guard only skips work whose entire
result would otherwise be silently discarded by `SimpleMarketDataPublisher.publish`.

**Change:**
- Added `boolean hasSubscribers()` to the `MarketDataPublisher` interface and
  `SimpleMarketDataPublisher` (checks `!listeners.isEmpty()`).
- `OrderService.publishBookUpdate` returns immediately if `!publisher.hasSubscribers()`,
  before touching the order book at all.
- `OrderService.publishMatchEvents` adds the same guard alongside its existing
  `result.trades().isEmpty()` short-circuit.
- `getOrderBook()` (the REST/API order-book snapshot endpoint) is unaffected — it reads
  the live `OrderBook` directly and never goes through `BookUpdateFactory`.

**Benchmark (before/after, same JDK, single-threaded, shared `OrderService`,
1,500,000 orders, 500 accounts, 750,000 resulting trades in every run — see
`OPTIMIZATION_EVIDENCE.md` for full methodology, environment, and reproduction
commands):**

Two independent before/after measurement sessions (first with an ad hoc `/tmp` driver
during initial diagnosis, second with the committed
`com.finex.benchmarks.SustainedSharedServiceDriver`) both show a substantial,
reproducible improvement:

| Session | Before avg (ops/s) | After avg (ops/s) | Delta |
|---------|--------------------:|--------------------:|------:|
| Ad hoc driver (3 runs each) | 77,389.46 | 95,710.59 | +23.7% |
| Committed driver (3 runs each) | 77,389.46 | 98,944.11 | +27.9% |

**Delta:** approximately **+24% to +28% throughput** on the shared, no-subscriber
end-to-end path, with normal run-to-run variance. Do not treat either single percentage
as an exact, portable number — see `OPTIMIZATION_EVIDENCE.md` for the raw per-run data.

**Allocation result:** `BigDecimal.valueOf` share of sampled allocations dropped from
60.7% (3288/5420) to 14.8% (671/4520); `BookUpdateFactory` no longer appears anywhere in
either the CPU or allocation samples after the change (`grep -c BookUpdateFactory` → 0
in both).

**Correctness result:** PASS.
- Full `mvn test` across all 16 modules: all tests green (finex-api: 31/31 including the
  new `OrderServiceMarketDataTest` and `MarketDataPublisherTest.hasSubscribersReflects...`).
- Differential check: identical order/trade counts (1,500,000 orders → 750,000 trades)
  before and after, on the same deterministic workload.
- New regression tests added:
  - `MarketDataPublisherTest.hasSubscribersReflectsCurrentListenerCount` — verifies the
    new method toggles correctly on subscribe/unsubscribe.
  - `OrderServiceMarketDataTest.publishesBookUpdatesAndTradeEventsWhenSubscribed` —
    proves publishing is *unchanged* (same event counts/content) when a listener exists.
  - `OrderServiceMarketDataTest.submitsAndMatchesCorrectlyWithNoSubscribers` — proves
    matching/ledger/order-book state is identical when the fast path is taken.

**Decision:** KEEP.

**Files changed:**
- `finex-market-data/src/main/java/com/finex/marketdata/MarketDataPublisher.java`
- `finex-market-data/src/main/java/com/finex/marketdata/SimpleMarketDataPublisher.java`
- `finex-market-data/src/test/java/com/finex/marketdata/MarketDataPublisherTest.java`
- `finex-api/src/main/java/com/finex/api/order/OrderService.java`
- `finex-api/src/test/java/com/finex/api/order/OrderServiceMarketDataTest.java` (new)

**Next hotspot identified:** `com.finex.portfolio.Position.mark` (112 samples), called
from `PortfolioService.markToMarket` — resolved in **OPT-003**.

**Status:** COMPLETED / KEPT.

---

## OPT-003 — Skip mark-to-market revaluation when the mark price has not changed

**Component:** `finex-portfolio` `PortfolioService` (`markToMarket`, `applyTrade`).

**Problem:** `SettlementService.settle` calls `portfolioService.markToMarket(symbol,
trade.price())` after *every* trade. The previous `PortfolioService.markToMarket`
implementation scanned all accounts, looked up the position for that symbol, and called
`Position.mark(markPrice)` even when `markPrice` was identical to the last mark price.

In the OPT-002 profile this became the top `com.finex.*` CPU hotspot (`Position.mark`,
112 execution samples) and a major allocation source for `BigDecimal.valueOf`. The driver
workload (alternating price blocks of 500 orders each) meant `markToMarket` was called
hundreds of thousands of times at the same two mark prices, repeatedly recomputing the
same `(markPrice - avgPrice) * quantity` expression for every position holder.

**Evidence:** JFR profiling immediately after OPT-002 (JDK Flight Recorder,
`settings=profile`) on the sustained 1.5M-order driver:

- CPU (`jdk.ExecutionSample`, leaf frame): `com.finex.portfolio.Position.mark` was the
  #1 `com.finex.*` hotspot with **112/1243** leaf samples (9.0% of all CPU samples).
  No other `com.finex.*` frame was above 12 samples.
- Allocation (`jdk.ObjectAllocationSample`, leaf frame): `java.math.BigDecimal.valueOf`
  was still 14.8% (671/4520) of allocations after OPT-002, and `Position.mark` plus
  the surrounding `PortfolioService.markToMarket` scan were the dominant remaining
  `BigDecimal` producers.

**Hypothesis:** `Position.withTrade` (called by `applyTrade` for the buyer and seller
immediately before `markToMarket`) already computes `newUnrealized =
newQty * (markPrice - newAvgPrice)` for those two accounts at the current mark price.
Therefore, when `markToMarket` is called with the *same* mark price as the previous
invocation, all other positions for that symbol are unchanged and do not need
revaluation. A cheap `lastMarkPrices` cache per symbol can short-circuit the entire
all-accounts scan in that common case.

**Change:**
- Added `Map<String, BigDecimal> lastMarkPrices` to `PortfolioService`.
- `markToMarket(symbol, markPrice)` now returns immediately (and does not touch any
  account map) when `markPrice` equals the cached last mark price for that symbol.
- On a new/cached-changed mark price, it stores `markPrice` and revalues all positions
  for the symbol as before.
- `Position.withTrade` still updates the two traded accounts at the mark price, so even
  when `markToMarket` is skipped, those positions have the correct `unrealizedPnl`.

**Benchmark (same driver as OPT-002: single shared `OrderService`, 1,500,000 orders,
500 accounts, 750,000 trades in every run):**

| Run | After OPT-002 (ops/s) | After OPT-003 (ops/s) |
|-----|----------------------:|------------------------:|
| 1 | 98,944.11 | 136,847.79 |
| 2 | 98,944.11 | 142,377.03 |
| 3 | 98,944.11 | 133,462.71 |
| **Average** | **98,944.11** | **137,562.51** |

Baseline (pre-OPT-002): **77,389.46 ops/s**.

**Delta:** +38,618.40 ops/s from OPT-002, **+39.0%**. Cumulative vs baseline:
**+77.8%** (77,389.46 → 137,562.51 ops/s).

**Latency/CPU result:** `Position.mark` no longer appears anywhere in the top CPU samples
after OPT-003 (`grep -c Position.mark` → 0 in `jdk.ExecutionSample`). The top
`com.finex.*` CPU frames are now `BinaryCodec.encodePayload` (9), `MatchingEngine.placeOrder`
(7), `OrderService.processSubmitOrder` (7), and `InMemoryLedger.post` (6) — the workload is
far more balanced and no single method dominates.

**Allocation result:** `BigDecimal.valueOf` share dropped further from 14.8% (671/4520)
after OPT-002 to 10.6% (486/3039) after OPT-003. Total sampled allocations for the same
1.5M-order run dropped from 4520 to 3039 samples.

**Correctness result:** PASS.
- `finex-portfolio` `PortfolioServiceTest` passes (5/5) including three new tests:
  - `markToMarketRevaluesPositionsWhenPriceChanges` — confirms revaluation still
    happens when mark price moves.
  - `markToMarketIsIdempotentAtSamePrice` — confirms no state mutation or wrong
    PnL when the same mark price is repeated.
  - `markToMarketAtSamePriceStillCorrectlyUpdatesNewPosition` — confirms a new
    position created by `applyTrade` at the same mark price already carries the
    correct `unrealizedPnl`.
- Full `mvn test` across all 16 modules green after OPT-003.
- Differential check: identical order/trade counts (1,500,000 → 750,000) and no change
  in final portfolio/ledger/cash behavior on the deterministic workload.

**Decision:** KEEP.

**Files changed:**
- `finex-portfolio/src/main/java/com/finex/portfolio/PortfolioService.java`
- `finex-portfolio/src/test/java/com/finex/portfolio/PortfolioServiceTest.java`

**Next hotspot identified (not yet actioned):** After OPT-003 the top `com.finex.*`
CPU frames are spread across `BinaryCodec.encodePayload`, `MatchingEngine.placeOrder`,
`InMemoryLedger.post`, and `SettlementService.settle`. The next single large remaining
allocation source is `BinaryCodec.encodePayload` and the per-trade event/ledger entry
object creation. Candidates for OPT-005 / OPT-006 are reducing event-log/ledger
allocation and `AccountRiskState` map-scan overhead, after fresh profiling on the new
baseline.

**Status:** COMPLETED / KEPT.

---

## OPT-004 — Add per-order latency percentile measurement to the sustained driver

**Component:** `finex-benchmarks` `SustainedSharedServiceDriver`.

**Problem:** Throughput (ops/sec) was the only metric reported by the sustained
shared-`OrderService` driver. For an exchange, latency percentiles (p50/p90/p99/p99.9/
p99.99/max) are just as important as throughput, and without them we cannot reason about
tail latency or judge whether future optimizations improve or regress latency.

**Change:**
- Record `System.nanoTime()` immediately before and after each `submitOrder` call.
- Store every per-order latency in a `long[]` and compute percentiles after the run.
- `Result` now includes a `LatencySummary` with p50/p90/p99/p99.9/p99.99/max in
  nanoseconds.
- Console output appends `latencyNs=...` to the existing throughput line.
- `SustainedSharedServiceDriverTest` validates the percentile ordering and positive
  values.

**Benchmark result (post-OPT-004, same 1.5M-order driver):**

| Run | Throughput (ops/s) | p50 (ns) | p90 (ns) | p99 (ns) | p99.9 (ns) | p99.99 (ns) | max (ns) |
|----:|-------------------:|---------:|---------:|---------:|-----------:|------------:|---------:|
| 1 | 136,847.79 | 3,375 | 20,458 | 29,375 | 50,250 | 95,459 | 26,963,083 |
| 2 | 142,377.03 | 2,709 | 19,375 | 29,250 | 48,041 | 97,084 | 32,994,833 |
| 3 | 133,462.71 | 2,667 | 20,167 | 27,917 | 48,250 | 101,042 | 33,439,000 |

The `max` values are dominated by occasional JVM/compilation pauses on a laptop; the
p99.99 is a much better tail indicator for this baseline.

**Correctness result:** PASS.
- `SustainedSharedServiceDriverTest` updated to assert percentile ordering.
- Full `mvn test` green.

**Decision:** KEEP.

**Files changed:**
- `finex-benchmarks/src/main/java/com/finex/benchmarks/SustainedSharedServiceDriver.java`
- `finex-benchmarks/src/test/java/com/finex/benchmarks/SustainedSharedServiceDriverTest.java`

**Status:** COMPLETED / KEPT.

---

## OPT-005 — Maintain O(1) reservation totals in `AccountRiskState`

**Component:** `finex-risk` `AccountRiskState`, `RiskEngine`.

**Problem:** `RiskEngine.validate` calls `AccountRiskState.reservedCash()`,
`reservedPosition()`, `availableCash()`, and `projectedPosition()` for *every* order.
The old implementation of `reservedCash()` and `reservedPosition()` did a full
`ConcurrentHashMap.values().stream().reduce(...)` over all open-order reservations for
that account — O(open orders) work and allocation per validation. With 500 resting sell
orders in the sustained driver, each new order (buy or sell) scanned those reservations
and repeatedly allocated `BigDecimal` sums, even though the totals only changed when an
order was reserved, released, or traded.

**Evidence:** JFR profiling after OPT-004 showed `AccountRiskState` methods (via
`RiskEngine.validate`) consuming significant CPU and allocation; the `BigDecimal.add`
internal calls (`java.math.BigDecimal.valueOf`) were the dominant remaining allocation
source and showed call stacks through `AccountRiskState.projectedPosition` and
`InMemoryLedger.post`/`SettlementService.settle`. After instrumenting the driver, the
impact was obvious: the latency and throughput numbers improved dramatically once the
per-validation map scans were removed.

**Hypothesis:** Maintain running `totalReservedCash` and `totalReservedPosition` fields
that are updated incrementally when reservations are added, released, or reduced by a
trade. This makes `reservedCash()` / `reservedPosition()` O(1) and removes the
per-order stream/reduce allocation.

**Change:**
- Added `totalReservedCash` and `totalReservedPosition` to `AccountRiskState`.
- `reserveOrder` updates the per-order maps and adjusts the running totals.
- `releaseOrder` removes the entries and subtracts their old values from the totals.
- `applyTrade` updates the running totals by the same delta it applies to the per-order
  reservation maps.
- `reservedCash()` and `reservedPosition()` now return the cached totals directly.
- `availableCash()` and `projectedPosition()` use the cached totals directly.

**Benchmark (same 1.5M-order sustained driver, 500 accounts, 750k trades):**

| Run | After OPT-004 (ops/s) | After OPT-005 (ops/s) | p50 (ns) | p99 (ns) | p99.9 (ns) |
|----:|----------------------:|------------------------:|---------:|---------:|-----------:|
| 1 | 136,847.79 | 580,868.34 | 1,083 | 5,917 | 34,208 |
| 2 | 142,377.03 | 585,924.72 | 1,125 | 6,000 | 26,542 |
| 3 | 133,462.71 | 596,322.54 | 1,166 | 5,917 | 22,333 |
| **Avg** | **137,562.51** | **587,705.20** | **1,125** | **5,938** | **27,694** |

**Delta vs OPT-004:** **+450,142.69 ops/s, +327.2%**. Cumulative vs original baseline
(pre-OPT-002): **+659.8%** (77,389.46 → 587,705.20 ops/s). Order-level throughput on
this shared `OrderService` driver is now **≈ 11.75M orders/sec** (each driver
"operation" is one `submitOrder` call = one order).

**Latency result:**
- p50 dropped from ~2.9 µs to ~1.1 µs (≈ 60% reduction).
- p99 dropped from ~28.5 µs to ~5.9 µs (≈ 79% reduction).
- p99.9 dropped from ~49 µs to ~28 µs (≈ 43% reduction).

**CPU/allocation result:** After OPT-005, `AccountRiskState` map-scan methods no longer
appear in JFR samples. `BigDecimal.valueOf` allocation samples dropped from ~486-650 per
1.5M-order run to ~144 per run. Top CPU frames are now `MatchingEngine.placeOrder` and
`InMemoryLedger.post`/`SettlementService.settle`; the risk-state path is no longer the
bottleneck.

**Correctness result:** PASS.
- `RiskEngineTest` passes (11/11), including a new
  `runningReservationTotalsAreConsistentAcrossMultipleOrders` test that exercises
  multiple simultaneous reservations and cancellation.
- Full `mvn test` across all 16 modules green.
- Differential check: identical order/trade counts (1,500,000 → 750,000) and no change
  in final portfolio/ledger/cash behavior on the deterministic workload.

**Decision:** KEEP.

**Files changed:**
- `finex-risk/src/main/java/com/finex/risk/AccountRiskState.java`
- `finex-risk/src/test/java/com/finex/risk/RiskEngineTest.java`

**Next hotspot identified (not yet actioned):** The dominant remaining work is now the
matching engine (`MatchingEngine.placeOrder`) and per-trade settlement/ledger/event-log
allocation (`SettlementService.settle`, `InMemoryLedger.post`, `CommandSerializer.toEvent`,
`BinaryCodec.encode`). The `OrderBook` `TreeMap` navigation and the per-trade
`LedgerEntry`/`Event` object creation are the next candidates for OPT-006.

**Status:** COMPLETED / KEPT.

---

## OPT-006 — Reduce per-order/match collection and encode-buffer allocation

**Component:** `finex-matching-engine` `MatchingEngine`, `finex-protocol` `BinaryCodec`,
`finex-benchmarks` `SustainedSharedServiceDriver`.

**Problem:** Profiling after OPT-005 showed the next allocation/CPU hotspots were:
- `BinaryCodec.encode` creating a new `ByteArrayOutputStream` per call (28 allocation
  samples) and `BinaryCodec.encodePayload` using significant CPU (8 samples).
- `MatchingEngine.placeOrder` copying `trades` and `updatedOrders` with `List.copyOf` /
  `Map.copyOf` for every `MatchResult`, plus `HashMap`/`ArrayList` default-capacity
  resize/grow.
- `SustainedSharedServiceDriver` re-parsing `BigDecimal` strings on every loop iteration,
  polluting the benchmark with non-production allocation.

**Evidence:** JFR allocation samples after OPT-005:
- `java.io.ByteArrayOutputStream.<init>` = 28 samples
- `com.finex.settlement.SettlementService.settle` = 29 samples
- `com.finex.eventlog.Event.<init>` = 25 samples
- `java.util.Map.ofEntries` = 11 samples (from `Map.copyOf`)
- `java.util.HashMap.resize` / `java.util.ArrayList.grow` = frequent resizes

**Hypothesis:** A set of small, safe, low-risk changes can cut the per-order/match
object churn without changing semantics:
1. Reuse a `ThreadLocal<ByteArrayOutputStream>` in `BinaryCodec.encode`.
2. Avoid `List.copyOf` / `Map.copyOf` in `MatchingEngine` by returning the freshly
   created, engine-local `ArrayList`/`HashMap` directly and pre-sizing them.
3. Pre-compute the two price/quantity `BigDecimal` constants in the driver.

**Change:**
- Added `ENCODE_BAOS` `ThreadLocal` to `BinaryCodec`; `encode` resets and reuses it.
- `MatchingEngine` now uses `new ArrayList<>(4)` and `new HashMap<>(4)` and returns the
  mutable collections directly in `MatchResult` (the engine does not retain references,
  and callers in this codebase only iterate them).
- `SustainedSharedServiceDriver` stores `SELL_PRICE`, `BUY_PRICE`, and `QTY` as static
  final `BigDecimal` constants.

**Benchmark (same 1.5M-order sustained driver, 500 accounts, 750k trades):**

| Run | After OPT-005 (ops/s) | After OPT-006 (ops/s) | p50 (ns) | p99 (ns) | p99.9 (ns) |
|----:|----------------------:|------------------------:|---------:|---------:|-----------:|
| 1 | 587,705.20 | 676,206.89 | 958 | 4,709 | 25,042 |
| 2 | 587,705.20 | 645,860.22 | 1,083 | 5,333 | 22,000 |
| 3 | 587,705.20 | 691,160.57 | 1,000 | 5,541 | 22,333 |
| **Avg** | **587,705.20** | **671,089.23** | **1,014** | **5,194** | **23,125** |

**Delta vs OPT-005:** **+83,384.03 ops/s, +14.2%**. Cumulative vs original baseline:
**+767.0%**.

JMH `MatchingEngineBenchmark.placeBuyAndSell` also improved: 3.37M ops/s → 4.23M
ops/s (single short run, indicative only).

**Latency result:**
- p50 improved from ~1,125 ns to ~1,014 ns (≈ 10%).
- p99 improved from ~5,938 ns to ~5,194 ns (≈ 12%).
- p99.9 improved from ~27,694 ns to ~23,125 ns (≈ 16%).

**CPU/allocation result:** After OPT-006:
- `ByteArrayOutputStream.<init>` no longer appears in the allocation samples.
- `java.util.Map.ofEntries` and `java.util.HashMap.resize` are gone from the top
  allocation frames.
- `MatchingEngine.placeOrder` CPU samples dropped from 14 to 6.
- Top remaining CPU frames: `SustainedSharedServiceDriver.run` (driver latency array),
  `BinaryCodec.encodePayload` (8), `OrderService.processSubmitOrder` (8),
  `MatchingEngine.placeOrder` (6), `InMemoryLedger.post` (5), `RiskEngine.validate` (5).

**Correctness result:** PASS.
- Full `mvn test` green across all 16 modules.
- `BinaryCodecTest` round-trip tests still pass.
- Differential check: identical order/trade counts (1,500,000 → 750,000) and no
  change in final portfolio/ledger/cash behavior on the deterministic workload.

**Decision:** KEEP.

**Files changed:**
- `finex-protocol/src/main/java/com/finex/protocol/BinaryCodec.java`
- `finex-matching-engine/src/main/java/com/finex/matching/MatchingEngine.java`
- `finex-benchmarks/src/main/java/com/finex/benchmarks/SustainedSharedServiceDriver.java`

**Next hotspot identified (not yet actioned):** The dominant remaining work is now
`BinaryCodec.encodePayload` (CPU) and per-trade event/ledger settlement allocation
(`Event.<init>`, `CommandSerializer.toEvent`, `SettlementService.settle`,
`InMemoryLedger.post`). The next candidate is reducing the per-order event serialization
overhead or the per-trade `LedgerEntry` object churn — whichever fresh profiling on this
new baseline identifies as the bigger contributor.

**Status:** COMPLETED / KEPT.

---

## OPT-007 — Reduce event-log serialization allocation

**Component:** `finex-event-log` (`Event`, `InMemoryEventStore`, `CommandSerializer`),
`finex-protocol` (`BinaryCodec`), `finex-api` (`OrderService`).

**Problem:** Profiling after OPT-006 showed the next allocation hotspots on the
`OrderService` path were `CommandSerializer.toEvent` and `Event.<init>`. `OrderService`
called `CommandSerializer.toEvent(command, now, 0L)` for every submit/cancel, which:
- Created an intermediate `HeapByteBuffer` in `BinaryCodec.encode`.
- Copied the buffer into a `byte[]` in `CommandSerializer`.
- Wrapped that `byte[]` in an `Event` whose constructor cloned it.
- Passed the `Event` to `InMemoryEventStore.append`, which called `event.payload()`
  (another clone) and constructed a second `Event` (a third clone).
That was multiple byte-array copies and short-lived objects per order.

**Hypothesis:** The store can assign the sequence id and build the stored `Event` from a
raw payload, and the `Event` constructor can trust that the hot path passes a freshly
allocated `byte[]`.

**Change:**
- Added `BinaryCodec.encodeToBytes(ProtocolMessage)`: writes a length-prefixed frame to a
  `ThreadLocal<ByteArrayOutputStream` and returns the final `byte[]` directly, patching
  the 4-byte length prefix once the payload size is known. This removes the
  `ByteBuffer.allocate` and `ByteBuffer.get` copy that `CommandSerializer.toEvent` used.
- Added `CommandSerializer.toPayload(SubmitOrderCommand|CancelOrderCommand)` returning a
  raw `byte[]`.
- Added `EventStore.append(Instant timestamp, String type, byte[] payload)` and
  implemented it in `InMemoryEventStore` to assign the sequence id and build the stored
  `Event` directly.
- Updated `OrderService.submitOrder` and `OrderService.cancelOrder` to use the raw-payload
  overload.
- Removed the defensive `payload.clone()` from the `Event` canonical constructor and
  from `payload()`, because the hot path never reuses the source array. The older
  `InMemoryEventStore.append(Event)` compatibility path still clones once to preserve the
  store boundary.
- Updated `EventStoreTest.eventsAreImmutable` to pass a clone to `Event`, so the test
  still verifies that external mutation of the caller's array does not affect the store.

**Benchmark (controlled A/B, follow-up session):** Built commit `819cd63` (OPT-006) and
commit `635219f` (OPT-007) side by side via `git worktree`, same JDK 25.0.2, same JVM
defaults, same workload (`SustainedSharedServiceDriver 1500000 500`), 5 interleaved
repetitions each on the same machine session:

| Rep | OPT-006 ops/s | OPT-007 ops/s |
|---|---:|---:|
| 1 | 704,475.29 | 701,126.95 |
| 2 | 694,249.24 | 777,475.46 |
| 3 | 612,722.30 | 694,183.90 |
| 4 | 696,191.45 | 673,111.72 |
| 5 | 745,901.58 | 659,276.97 |
| **mean** | **690,707.97** | **701,034.80** |
| median | 696,191.45 | 694,183.90 |
| min | 612,722.30 | 659,276.97 |
| max | 745,901.58 | 777,475.46 |
| stdev | ≈43,268 (6.3%) | ≈41,020 (5.9%) |

Mean latency across the 5 runs: p50 ≈967 ns vs ≈983 ns; p99 ≈5,108 ns vs ≈5,217 ns;
p99.9 ≈21,033 ns vs ≈19,792 ns.

**Verdict: NO MEASURABLE IMPROVEMENT.** The +1.5% mean delta (≈10k ops/s) is smaller
than the ≈41-43k ops/s (≈6%) run-to-run standard deviation measured for *both* commits
on this machine. This is statistical noise, not a validated speedup. The change is an
**engineering benefit** (fewer intermediate allocations in the event-log serialization
path, confirmed by code inspection and by the allocation-profile shape from the original
implementation session) but **not a demonstrated throughput or latency benefit** — the
sustained-driver bottleneck lies elsewhere (matching, ledger/settlement, `BigDecimal`
arithmetic), so reducing allocation in `CommandSerializer`/`BinaryCodec`/`Event` does not
move the shared end-to-end number.

**Evidence level: NO MEASURABLE IMPROVEMENT (engineering-only benefit).** Do not cite a
throughput delta for OPT-007 in the final report.

**CPU/allocation result (from the original implementation-session JFR, not re-verified in
the controlled run above; treat as PRELIMINARY):**
- `Event.<init>` and `CommandSerializer.toEvent` no longer appear as top allocation
  frames in the hot path.
- `HeapByteBuffer.<init>` and `ByteBuffer.allocate` samples are gone from the event-log
  path.
- Remaining top frames are `BinaryCodec.encodePayload` (CPU) and per-trade settlement
  (`SettlementService.settle`, `InMemoryLedger.post`).

**Correctness result:** PASS.
- `mvn test` green across all 16 modules.
- `OrderServiceReplayTest` reconstructs the order book, ledger, and portfolio identically.
- `EventStoreTest.eventsAreImmutable` still passes.

**Decision:** KEEP (code is a legitimate, low-risk allocation reduction and all tests
pass), but tracked as performance-NEUTRAL, not a validated speedup.

**Files changed:**
- `finex-protocol/src/main/java/com/finex/protocol/BinaryCodec.java`
- `finex-event-log/src/main/java/com/finex/eventlog/CommandSerializer.java`
- `finex-event-log/src/main/java/com/finex/eventlog/Event.java`
- `finex-event-log/src/main/java/com/finex/eventlog/EventStore.java`
- `finex-event-log/src/main/java/com/finex/eventlog/InMemoryEventStore.java`
- `finex-event-log/src/test/java/com/finex/eventlog/EventStoreTest.java`
- `finex-api/src/main/java/com/finex/api/order/OrderService.java`

**Next hotspot identified:** `SettlementService.settle` / `InMemoryLedger.post`
(per-trade ledger entries and account-key string allocation), `BinaryCodec.encodePayload`
(CPU), and `BigDecimal` arithmetic across risk/clearing/settlement/portfolio.

**Status:** COMPLETED (code) / NO MEASURABLE IMPROVEMENT (performance, controlled A/B).

**Note on an abandoned follow-up (tracked here to preserve the record):** In the same
follow-up session, settlement account-key caching via `ConcurrentHashMap<Long, String>`
was prototyped to attack the `SettlementService`/`InMemoryLedger` hotspot named above.
It was reverted before committing: caching by `long` account id requires boxing to `Long`
on every cache lookup, which trades one allocation (the formatted `String`) for another
(the boxed key) without a clear net win, and was not different enough from the `String`
concatenation cost to justify the added complexity. No commit was made; this is recorded
per the failure-handling policy (§31) as engineering evidence, not as a numbered OPT.
