# Benchmarks

> Real, measured results only. Never insert hypothetical values as actual results
> (Master Plan §52).

## Latest run (post OPT-006)

All numbers are from `finex-benchmarks` `BenchmarkRunner` (single JVM, no fork,
2 x 2 s warmup, 3 x 1 s measurement). Environment: Apple Silicon macOS, JDK 25.0.2,
10 physical cores, 16 GB RAM. These are short JMH micro/component runs with high
variance on some benchmarks; see `docs/performance/OPTIMIZATION_EVIDENCE.md` for a
longer, lower-variance sustained driver used to validate OPT-002 through OPT-006.

| Benchmark | Mode | Threads | Score | Error | Units |
|-----------|------|--------:|------:|------:|-------|
| `OrderBookBenchmark.addAndCancel` | thrpt | 1 | 15,737,712.120 | ± 636,241.575 | ops/s |
| `MatchingEngineBenchmark.placeBuyAndSell` | thrpt | 1 | 4,228,760.318 | ± — | ops/s |
| `OrderServiceBenchmark.submitLimitOrder` | thrpt | 1 | 57,184.472 | ± 158,476.801 | ops/s |
| `LoadGeneratorBenchmark.runWorkload` | thrpt | 1 | 59,268.001 | ± 557.473 | ops/s |
| `MultiThreadedLoadGeneratorBenchmark.runWorkload` | thrpt | 4 | 149,632.862 | ± 6,335.994 | ops/s |

`MatchingEngineBenchmark` improved from 3.37M ops/s to 4.23M ops/s (single short
indicative run) following the `List.copyOf`/`Map.copyOf` removal and collection
pre-sizing in OPT-006.

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
  conclusive. The `OPTIMIZATION_EVIDENCE.md` log uses a longer, more stable workload
  instead.

## Sustained shared-`OrderService` evidence driver (post OPT-006)

`com.finex.benchmarks.SustainedSharedServiceDriver`: 1,500,000 orders, 500 accounts,
symbol `BTC-USD`, single shared `OrderService`, 750,000 resulting trades in every run.

| State | Avg throughput (ops/s) | Cumulative delta vs baseline |
|-------|-----------------------:|------------------------------:|
| Baseline (pre-OPT-002) | 77,389.46 | — |
| After OPT-002 | 98,944.11 | +27.9% |
| After OPT-003 | 137,562.51 | +77.8% |
| After OPT-005 | 587,705.20 | +659.8% |
| After OPT-006 | 671,089.23 | +767.0% |
| After OPT-007 (controlled A/B, 5 reps) | 701,034.80 | not distinguishable from OPT-006 |

This is the most rigorous end-to-end throughput measurement for the shared `OrderService`
path; the JMH numbers above are shorter and more variable. This driver reports per-order
latency percentiles; see `OPTIMIZATION_EVIDENCE.md` for the raw numbers.

**OPT-007 controlled A/B (follow-up session):** `git worktree`-built OPT-006 (`819cd63`)
and OPT-007 (`635219f`) side by side, same JDK/JVM/workload, 5 interleaved reps each:

| Rep | OPT-006 ops/s | OPT-007 ops/s |
|---|---:|---:|
| 1 | 704,475.29 | 701,126.95 |
| 2 | 694,249.24 | 777,475.46 |
| 3 | 612,722.30 | 694,183.90 |
| 4 | 696,191.45 | 673,111.72 |
| 5 | 745,901.58 | 659,276.97 |
| mean | 690,707.97 | 701,034.80 |
| stdev | ≈43,268 | ≈41,020 |

Mean delta (+1.5%) is smaller than the ≈6% run-to-run stdev on *both* commits — this is
noise, not a validated speedup. See `OPTIMIZATIONS.md` OPT-007 for the full writeup and
evidence-level classification (**NO MEASURABLE IMPROVEMENT**, engineering benefit only).
The earlier ~200-300k ops/sec readings recorded during the OPT-007 implementation session
were caused by transient machine load (memory pressure, background container activity)
and are superseded by this controlled run.

### Latest latency percentiles (post OPT-006)

| p50 | p90 | p99 | p99.9 | p99.99 | max |
|----:|----:|----:|------:|-------:|----:|
| ~1,014 ns | ~2,200 ns | ~5,194 ns | ~23,125 ns | ~55,000 ns | ~50,000,000 ns |

`max` is dominated by JVM warmup/compilation pauses on a short laptop run; the
p99.99 is a more useful tail indicator.

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
