# Benchmarks

> Real, measured results only. Never insert hypothetical values as actual results
> (Master Plan §52).

## Latest run (Phase 24)

All numbers are from `finex-benchmarks` `BenchmarkRunner` (single JVM, no fork,
2 x 2 s warmup, 3 x 1 s measurement). Environment: Apple Silicon macOS, JDK 25.0.2.

| Benchmark | Mode | Threads | Score | Error | Units |
|-----------|------|--------:|------:|------:|-------|
| `OrderBookBenchmark.addAndCancel` | thrpt | 1 | 15,140,417.887 | ± 1,177,908.378 | ops/s |
| `MatchingEngineBenchmark.placeBuyAndSell` | thrpt | 1 | 3,541,011.595 | ± 3,811,543.298 | ops/s |
| `OrderServiceBenchmark.submitLimitOrder` | thrpt | 1 | 53,249.381 | ± 105,040.159 | ops/s |
| `LoadGeneratorBenchmark.runWorkload` | thrpt | 1 | 50,175.949 | ± 2,631.315 | ops/s |
| `MultiThreadedLoadGeneratorBenchmark.runWorkload` | thrpt | 4 | 134,692.199 | ± 25,368.168 | ops/s |

Notes:
- `LoadGeneratorBenchmark` and `MultiThreadedLoadGeneratorBenchmark` each run a
  20-order workload per invocation; their order-level throughput is ~1.0M and
  ~2.7M orders/sec respectively when multiplied by 20.
- `OrderServiceBenchmark` exercises risk, settlement, ledger, portfolio, and event-log
  appends for every order.
- `MultiThreadedLoadGeneratorBenchmark` uses four threads, each with its own fresh
  `OrderService`, so it measures aggregate throughput without shared-state contention.

For the honest 1M/sec assessment and roadmap, see `FINAL_BENCHMARK_REPORT.md`.

## Environment

- Apple Silicon (M-series) macOS, JDK 25.0.2, single JVM, no fork.
- Baseline single-threaded benchmarks; the production target will require multi-core,
  lock-free, and sharded work-stealing optimizations (see `FINAL_BENCHMARK_REPORT.md`).

## Run commands

```bash
# All benchmarks
mvn -pl finex-benchmarks -DskipTests package exec:java \
  -Dexec.mainClass=com.finex.benchmarks.BenchmarkRunner

# JFR profile
mvn -pl finex-benchmarks exec:java \
  -Dexec.mainClass=com.finex.benchmarks.ProfileRunner \
  -Dexec.args="/tmp/finex-profile.jfr"
```
