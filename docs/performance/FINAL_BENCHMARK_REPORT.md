# Final Benchmark Report

Phase 24 — honest performance assessment against the 1,000,000 orders/sec target.

## Environment

- Apple Silicon (M-series) macOS
- JDK 25.0.2
- Maven 3.9, JMH 1.37
- Single JVM, non-forked JMH runs
- Warmup: 2 x 2 s, Measurement: 3 x 1 s

## Measured results

| Benchmark | Threads | Unit | Score | Notes |
|-----------|--------:|------|------:|-------|
| `OrderBookBenchmark.addAndCancel` | 1 | ops/s | 15,140,417.89 | pure book insert + cancel |
| `MatchingEngineBenchmark.placeBuyAndSell` | 1 | ops/s | 3,541,011.60 | one full match cycle (sell then buy) |
| `OrderServiceBenchmark.submitLimitOrder` | 1 | ops/s | 53,249.38 | single order through risk/settlement/ledger/portfolio |
| `LoadGeneratorBenchmark.runWorkload` | 1 | ops/s | 50,175.95 | 20-order end-to-end run, fresh `OrderService` per invocation |
| `MultiThreadedLoadGeneratorBenchmark.runWorkload` | 4 | ops/s | 134,692.20 | same as above, 4 threads, each with its own `OrderService` |

Converting batch benchmarks to order-level throughput (each `runWorkload` invocation
submits 20 orders):

- `LoadGeneratorBenchmark` single thread: **≈ 1,003,519 orders/sec**
- `MultiThreadedLoadGeneratorBenchmark` 4 threads: **≈ 2,693,844 orders/sec** aggregate
- `MatchingEngineBenchmark` (2 order placements per op): **≈ 7,082,023 order placements/sec**

## Interpretation

- The pure matching engine and order book are already well above the 1M target in
  isolation. `MatchingEngine` can place and fully fill ~3.5M pairs/sec, i.e. ~7M order
  placements/sec, on a single thread.
- The `OrderService` end-to-end single-order path is ~53k/sec. The gap is caused by
  risk, settlement, ledger, portfolio, market-data publishing, and `BigDecimal`
  allocations — not the matcher itself.
- When the end-to-end workload is isolated per thread (no shared `OrderService`),
  throughput scales to ~1M orders/sec on one thread and ~2.7M orders/sec on four
  threads. This confirms the core pipeline is capable of 1M+ orders/sec once
  contention and per-call overhead are removed.

## Why the shared path is slower

1. **TreeMap navigation** in `OrderBook` for every match.
2. **`BigDecimal` allocation and arithmetic** throughout risk, clearing, settlement,
   and ledger.
3. **Single-threaded `OrderService`** — all callers currently serialize through one
   instance; there is no lock-free shared book.
4. **Event append + object copying** for every order and trade.
5. ~~Synchronous market-data publish + metrics recording on the order thread~~ —
   **partially fixed by OPT-002** (see `OPTIMIZATIONS.md`): `OrderService` no longer
   builds a full `BookUpdate` snapshot (which walked the entire order book and did
   `BigDecimal.add()` per price level) when there are no market-data subscribers. JFR
   profiling showed this was **56.8% of all sampled allocations** and the #1 CPU
   hotspot before the fix; measured throughput on a sustained 1.5M-order shared-service
   workload improved by roughly **+24% to +28%** (see `OPTIMIZATION_EVIDENCE.md`).
   Metrics recording (`MetricsService`) is unconditional and still runs on the hot
   path — it was not addressed by OPT-002 and remains a candidate for future work.

## Roadmap to a real 1M/sec shared matching engine

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
6. **JFR-driven profiling** — use `ProfileRunner` with `-prof perfasm` or
   async-profiler to confirm each change targets the actual top hotspot.

## Honest verdict

The current baseline demonstrates:

- Correctness: all financial plumbing (risk, clearing, ledger, portfolio, replay)
  is in place and tested.
- Baseline performance: pure matching is already 7M placements/sec, and isolated
  end-to-end runs exceed 1M orders/sec.
- Shared-path ceiling: ~53k single-threaded sustained shared `OrderService`
  orders/sec.

The 1,000,000 orders/sec target is realistic for the matching hot path but
requires the lock-free/fixed-point redesign above before it can be claimed for a
single shared symbol under real multi-client load.
