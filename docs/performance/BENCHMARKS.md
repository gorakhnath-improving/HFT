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

**OPT-010 controlled A/B:** 300,000 orders, 500 accounts, `-Xms2g -Xmx2g`, five
interleaved fresh-JVM repetitions per numeric mode:

| Rep | BigDecimal ops/s | Fixed-point ops/s |
|---|---:|---:|
| 1 | 543,148.55 | 626,553.28 |
| 2 | 558,424.20 | 643,083.48 |
| 3 | 565,752.46 | 625,864.96 |
| 4 | 563,455.58 | 589,064.80 |
| 5 | 548,930.48 | 550,839.97 |
| mean | 555,942.26 | 607,081.30 |
| median | 558,424.20 | 625,864.96 |
| stdev | 9,639.69 | 37,134.29 |

Mean delta: **+9.2%**. Median latency across repetitions was BigDecimal/fixed-point:
p50 1,041/916 ns, p90 2,709/2,334 ns, p99 11,167/10,000 ns, p99.9
33,083/29,041 ns, p99.99 110,292/102,041 ns, max 37,674,958/38,407,084 ns.
JFR allocation samples showed BigDecimal 151→139, Long 67→93, equal total allocation
samples (660), and 8 young collections in each recording. See `OPTIMIZATIONS.md` OPT-010
for correctness, scale/range, profiling caveats, and the full decision.

**OPT-011 rejected experiment:** Removing a redundant incoming-order cache write was tested with
five interleaved, isolated-build 1.5M-order fixed-point repetitions. Baseline was 784,735 mean
ops/s (81,539 stdev); candidate was 811,992 (38,262 stdev), +3.47%. Paired deltas ranged from
−9.7% to +19.5%, so the result is inside the baseline's 10.4% variation. JFR showed the targeted
`ConcurrentHashMap.putVal` sample share fall from 12.5% to 3.15%, but no reliable total
allocation, GC, or latency improvement. The code was reverted. See `EXPERIMENTS.md` and
`OPTIMIZATIONS.md` OPT-011.

**OPT-012 controlled A/B:** Primitive fixed-point reservation maps, ten isolated interleaved
1.5M-order pairs: baseline 758,374 mean / 754,974 median / 39,091 stdev; candidate 796,988 mean /
788,250 median / 31,639 stdev. Mean delta **+5.09%**, median +4.41%, with 8/10 pairs positive.
Median latency p50 875→875 ns, p90 1,750→1,605, p99 4,792→4,604, p99.9 21,063→16,459,
p99.99 44,730→42,792, max 74.3→70.8 ms. JFR: Long allocation pressure 24.84%→11.26%,
ConcurrentHashMap node 9.77%→7.18%, young GC 18→16, total pauses 982→747 ms.

**OPT-013 controlled A/B:** Consolidating three primitive reservation maps into one parallel-value
table, five valid isolated interleaved 1.5M-order pairs: OPT-012 baseline 829,499 mean / 841,662
median; candidate 945,413 mean / 946,580 median. Mean delta **+13.97%**, median +12.47%.
Median p50 875→791 ns, p90 1,375→1,083, p99 4,125→3,917, p99.9 16,917→12,250,
p99.99 43,167→41,375; max regressed 71.2→75.4 ms. JFR Long pressure 11.26%→2.39%,
map-put CPU 16.33%→5.42%, young GC 16→12; total/max GC pause regressed 747→779 ms and
127→224 ms.

## Final reproducibility run (portfolio finalization)

`mvn test` passed across all 16 modules (19 test suites) immediately before this run. As a
final, honest reproducibility check (not a new optimization claim), the sustained driver was
re-run once more, fixed-point mode, 5 fresh-JVM repetitions, 1.5M orders / 500 accounts:

| Rep | ops/sec |
|---|---:|
| 1 | 556,768 |
| 2 | 402,618 |
| 3 | 233,305 |
| 4 | 439,827 |
| 5 | 388,336 |
| mean | 404,171 |
| median | 402,618 |
| stdev | 116,195 |

This is markedly lower than the 945,413 ops/sec paired-comparison figure recorded for OPT-013.
The machine showed `load average 6.73` (10 cores) and ~5.8 GB under memory compression at the
time — i.e. this run was not on a quiet machine, and the drop is consistent with the
machine-load sensitivity already documented during the OPT-007 session (see
`FINAL_BENCHMARK_REPORT.md` and `PROGRESS.md`). No code changed between the OPT-013 paired
benchmark and this run; both numbers are genuine measurements of the same code under different
load conditions. This is reported here rather than omitted, per the project's evidence-honesty
standard — see `FINAL_BENCHMARK_REPORT.md` for the full explanation.

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
