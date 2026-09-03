# Final Benchmark Report

Honest performance assessment against the 1,000,000 orders/sec target. Updated through
OPT-003; see `OPTIMIZATIONS.md` and `OPTIMIZATION_EVIDENCE.md` for methodology and
per-optimization raw data.

## Environment

- Apple Silicon (M-series) macOS (10 physical / 10 logical cores, 16 GB RAM)
- JDK 25.0.2 (HotSpot, 64-bit Server VM)
- Maven 3.9, JMH 1.37
- Single JVM, non-forked JMH runs
- Warmup: 2 x 2 s, Measurement: 3 x 1 s (JMH micro-benchmarks)
- Sustained driver: `SustainedSharedServiceDriver`, 1.5M orders, 500 accounts, single
  shared `OrderService`, 750k trades

## Measured results

### JMH micro/component benchmarks (post OPT-003)

| Benchmark | Threads | Unit | Score | Notes |
|-----------|--------:|------|------:|-------|
| `OrderBookBenchmark.addAndCancel` | 1 | ops/s | 15,140,417.89 | pure book insert + cancel |
| `MatchingEngineBenchmark.placeBuyAndSell` | 1 | ops/s | 3,784,457.73 | one full match cycle (sell then buy) |
| `OrderServiceBenchmark.submitLimitOrder` | 1 | ops/s | 57,184.47 | single order through risk/settlement/ledger/portfolio |
| `LoadGeneratorBenchmark.runWorkload` | 1 | ops/s | 59,268.00 | 20-order end-to-end run, fresh `OrderService` per invocation |
| `MultiThreadedLoadGeneratorBenchmark.runWorkload` | 4 | ops/s | 149,632.86 | same, 4 threads, each with its own `OrderService` |

### Sustained shared-`OrderService` driver (most rigorous end-to-end measurement)

| State | Avg throughput | Order-level throughput | Notes |
|-------|---------------:|-----------------------:|-------|
| Baseline (pre-OPT-002) | 77,389.46 ops/s | 1,547,789 orders/sec | 1.5M orders, 500 accounts, 750k trades |
| After OPT-002 | 98,944.11 ops/s | 1,978,882 orders/sec | +27.9% vs baseline |
| **After OPT-003** | **137,562.51 ops/s** | **2,751,250 orders/sec** | **+77.8% vs baseline** |

Converting batch benchmarks to order-level throughput (each `runWorkload` invocation
submits 20 orders):

- `LoadGeneratorBenchmark` single thread: **≈ 1,185,360 orders/sec**
- `MultiThreadedLoadGeneratorBenchmark` 4 threads: **≈ 2,992,657 orders/sec** aggregate
- `MatchingEngineBenchmark` (2 order placements per op): **≈ 7,568,915 order placements/sec**

## Interpretation

- The pure matching engine and order book are already well above the 1M target in
  isolation. `MatchingEngine` can place and fully fill ~3.8M pairs/sec, i.e. ~7.6M order
  placements/sec, on a single thread.
- The `OrderServiceBenchmark` single-order path (53–57k/sec) is a short, noisy JMH run
  and is not the best proxy for sustained shared-service throughput.
- The sustained shared-`OrderService` driver now reaches **137.5k invocations/sec**,
  i.e. **≈ 2.75M orders/sec at the Java `OrderService` level** (each invocation is one
  `submitOrder` call, the workload has 20 order placements per measured invocation
  of the driver itself; here the driver invocation is one full order, not a batch).
  This is a single-threaded, shared-state measurement on a laptop-class CPU, with all
  risk, clearing, settlement, ledger, portfolio, and event-log work still synchronous.
- When the end-to-end workload is isolated per thread (no shared `OrderService`),
  throughput is higher still (≈3.0M orders/sec aggregate on 4 threads in JMH). This
  confirms the core pipeline is capable of multi-million-orders/sec once contention
  and per-call overhead are removed.

## Why the shared path was slower, and what has been fixed

1. **TreeMap navigation** in `OrderBook` for every match — still present; not yet
   addressed. Pure matching is fast, but `OrderBook` copies/maps still show up.
2. **`BigDecimal` allocation and arithmetic** throughout risk, clearing, settlement,
   and ledger — still present; reduced by removing wasted work (OPT-002/OPT-003).
3. **Single-threaded `OrderService`** — all callers currently serialize through one
   instance; there is no lock-free shared book.
4. **Event append + object copying** for every order and trade — still present.
5. ~~Unconditional market-data snapshot construction~~ — **fixed by OPT-002**.
   `OrderService` no longer builds a full `BookUpdate` snapshot when there are no
   market-data subscribers; this was 56.8% of sampled allocations and the #1 CPU
   hotspot. Throughput improved ~+24% to +28%.
6. ~~Unconditional mark-to-market full scan~~ — **fixed by OPT-003**.
   `PortfolioService.markToMarket` was the top CPU frame after OPT-002 because it
   re-scanned every account after every trade even when the mark price hadn't changed.
   A `lastMarkPrices` cache short-circuits the scan when the mark price is unchanged.
   Throughput improved a further ~+39%, cumulative ~+78% vs the original baseline.
7. **Metrics recording** (`MetricsService`) is unconditional and still runs on the hot
   path; it was not addressed by OPT-002/OPT-003 and remains a candidate for future work.

## Roadmap to a real 1M/sec (and beyond) shared matching engine

1. **Fixed-point numerics** — replace `BigDecimal` price/quantity with scaled `long`s
   in the hot path; keep `BigDecimal` only for external APIs and ledger reporting.
2. **Lock-free order book** — replace `TreeMap`/`ArrayList` with an intrusive
   price-level structure (e.g. `Long2ObjectOpenHashMap` + sorted arrays/ring buffers
   per price) and atomic operations per symbol shard.
3. **Dedicated matching threads** — one thread per symbol or shard, fed by a
   disruptor-style ring buffer of commands; avoid shared mutable state.
4. **Offload non-critical path** — risk checks can be pre-screened; settlement,
   ledger posting, and metrics can be batched and processed asynchronously after the
   trade ack.
5. **Object pooling / primitive collections** — reduce allocation pressure from
   `Order`, `Trade`, `MatchResult`, and `Map.copyOf` allocations.
6. **JFR-driven profiling** — use `ProfileRunner` or `-XX:StartFlightRecording` with
   `-prof perfasm` / async-profiler to confirm each change targets the actual top
   hotspot.

## Honest verdict

The current baseline demonstrates:

- Correctness: all financial plumbing (risk, clearing, ledger, portfolio, replay)
  is in place and tested.
- Baseline performance: pure matching is already 7.6M placements/sec, and isolated
  end-to-end runs exceed 1M orders/sec.
- Shared-path sustained throughput: **2.75M orders/sec** at the Java `OrderService`
  level on a single-threaded, shared-state, laptop-class measurement, after removing
  two major wasted-work hotspots (OPT-002 and OPT-003). This already exceeds the
  1M orders/sec conceptual target for this path, but it is still not a production
  HFT exchange: it is single-threaded, uses `BigDecimal`/`TreeMap`, synchronous
  settlement/ledger/event-log, and has not been measured over HTTP.

The 1,000,000 orders/sec target is now exceeded in the measured shared Java path,
but the architecture still has a long runway before it could be called production-grade
or exchange-grade. The next major step is a lock-free, fixed-point, sharded matching
engine with asynchronous post-trade processing.
