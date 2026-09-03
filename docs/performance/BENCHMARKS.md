# Benchmarks

> Placeholder — real, measured results only, recorded starting Phase 17.
> Never insert hypothetical values as actual results (Master Plan §52).

## Baseline (Phase 17)

All numbers are from `finex-benchmarks` running with the `BenchmarkRunner` defaults
(2 x 2 s warmup, 3 x 1 s measurement, single-JVM/forkless). These are for shape and
relative comparison, not final 1M orders/sec claims.

| Benchmark | Mode | Score | Error | Units |
|-----------|------|------:|------:|-------|
| `OrderBookBenchmark.addAndCancel` | thrpt | 15,067,286.928 | ± 1,507,507.491 | ops/s |
| `MatchingEngineBenchmark.placeBuyAndSell` | thrpt | 3,655,769.322 | ± 78,654.943 | ops/s |
| `OrderServiceBenchmark.submitLimitOrder` | thrpt | 55,762.863 | ± 250,163.130 | ops/s |
| `LoadGeneratorBenchmark.runWorkload` | thrpt | 53,031.518 | ± 2,714.492 | ops/s |

## After Phase 19 optimization (in-place resting-order update)

| Benchmark | Mode | Score | Error | Units |
|-----------|------|------:|------:|-------|
| `OrderBookBenchmark.addAndCancel` | thrpt | 14,607,902.767 | ± 10,779,094.685 | ops/s |
| `MatchingEngineBenchmark.placeBuyAndSell` | thrpt | 3,713,991.701 | ± 93,541.460 | ops/s |
| `OrderServiceBenchmark.submitLimitOrder` | thrpt | 58,178.307 | ± 111,123.805 | ops/s |
| `LoadGeneratorBenchmark.runWorkload` | thrpt | 54,359.530 | ± 1,705.161 | ops/s |

Notes:
- `OrderServiceBenchmark` exercises risk, settlement, ledger, portfolio, and event-log appends
  for every order; this is why it is much lower than the pure matching engine.
- `LoadGeneratorBenchmark` runs 20-order workloads against a fresh `OrderService` each invocation,
  so it includes service construction and teardown in the measurement.
- Run command: `mvn -pl finex-benchmarks -DskipTests package exec:java -Dexec.mainClass=com.finex.benchmarks.BenchmarkRunner`

## Environment

- Apple Silicon (M-series) macOS, JDK 25.0.2, single JVM, no fork.
- Single-threaded benchmarks; the production target will require multi-core, lock-free, and
  sharded work-stealing optimizations (Phases 18-19, 24).
