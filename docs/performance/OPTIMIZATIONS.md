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
object creation. A strong candidate for OPT-004 is reducing allocation in the event-log
path (e.g. pooled buffers or primitive serialization), but only after profiling on the
new baseline confirms it is the actual bottleneck.

**Status:** COMPLETED / KEPT.
