# Benchmarks

> Real, measured results only. Never insert hypothetical values as actual results
> (Master Plan §52).

## Latest run (post OPT-002)

All numbers are from `finex-benchmarks` `BenchmarkRunner` (single JVM, no fork,
2 x 2 s warmup, 3 x 1 s measurement). Environment: Apple Silicon macOS, JDK 25.0.2,
10 physical cores, 16 GB RAM. These are short JMH micro/component runs; see
`docs/performance/OPTIMIZATION_EVIDENCE.md` for a longer, lower-variance measurement
used to validate OPT-002.

| Benchmark | Mode | Threads | Score | Error | Units |
|-----------|------|--------:|------:|------:|-------|
| `OrderBookBenchmark.addAndCancel` | thrpt | 1 | 15,737,712.120 | ± 636,241.575 | ops/s |
| `MatchingEngineBenchmark.placeBuyAndSell` | thrpt | 1 | 3,869,429.897 | ± 253,421.008 | ops/s |
| `OrderServiceBenchmark.submitLimitOrder` | thrpt | 1 | 57,184.472 | ± 158,476.801 | ops/s |
| `LoadGeneratorBenchmark.runWorkload` | thrpt | 1 | 59,268.001 | ± 557.473 | ops/s |
| `MultiThreadedLoadGeneratorBenchmark.runWorkload` | thrpt | 4 | 149,632.862 | ± 6,335.994 | ops/s |

Notes:
- `LoadGeneratorBenchmark` and `MultiThreadedLoadGeneratorBenchmark` each run a
  20-order workload per invocation; their order-level throughput is ~1.19M and
  ~3.0M orders/sec respectively when multiplied by 20.
- `OrderServiceBenchmark` exercises risk, settlement, ledger, portfolio, and event-log
  appends for every order.
- `MultiThreadedLoadGeneratorBenchmark` uses four threads, each with its own fresh
  `OrderService`, so it measures aggregate throughput without shared-state contention.
- These short (1s measurement) JMH runs have high relative error on some benchmarks
  (e.g. `OrderServiceBenchmark` ± 158k on 57k, i.e. ~277% CI) due to only 3 short
  iterations; treat single-run deltas on these specific benchmarks as indicative, not
  conclusive. The OPT-002 evidence log uses a longer, more stable workload instead.

## Prior run (pre OPT-002, for comparison)

| Benchmark | Score (ops/s) |
|-----------|---------------:|
| `OrderBookBenchmark.addAndCancel` | 15,140,417.887 |
| `MatchingEngineBenchmark.placeBuyAndSell` | 3,541,011.595 |
| `OrderServiceBenchmark.submitLimitOrder` | 53,249.381 |
| `LoadGeneratorBenchmark.runWorkload` | 50,175.949 |
| `MultiThreadedLoadGeneratorBenchmark.runWorkload` | 134,692.199 |

For the honest 1M/sec assessment and roadmap, see `FINAL_BENCHMARK_REPORT.md`. For the
OPT-002 methodology and rigorous before/after evidence, see `OPTIMIZATIONS.md` and
`OPTIMIZATION_EVIDENCE.md`.

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
