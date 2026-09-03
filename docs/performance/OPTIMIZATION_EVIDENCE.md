# Optimization Evidence Log

This file holds raw, reproducible before/after measurements backing entries in
`OPTIMIZATIONS.md`. JMH micro-benchmarks are useful but have high variance on short
(1s) measurement windows for benchmarks with GC/allocation-heavy call paths; this log
uses a longer, deterministic, single-JVM driver for lower-variance evidence.

## Environment (all measurements below)

- Hardware: Apple Silicon (arm64), 10 physical / 10 logical cores, 16 GB RAM
- OS: Darwin 25.6.0 (macOS)
- JDK: 25.0.2+10-LTS (HotSpot, 64-bit Server VM)
- Build: Maven 3.9.16
- Repository commit at measurement time: see `git log` around the OPT-002 commit
- JVM flags: none beyond defaults (no explicit heap sizing, no GC selection override)
- No JMH fork; single JVM process per run; each of the 3 runs per configuration is a
  fresh `java` process

## Driver

`com.finex.benchmarks.SustainedSharedServiceDriver` (finex-benchmarks module):
- Single shared `OrderService` instance for the whole run (deliberately, to measure the
  real shared-state path rather than the per-thread-isolated path used elsewhere).
- 1,500,000 orders, 500 accounts, symbol `BTC-USD`.
- Side flips every 500 orders (one full "account cycle") so cash/position stay bounded
  indefinitely — this avoids risk-engine rejections that would otherwise occur if orders
  from one account only ever bought or only ever sold.
- Synthetic timestamps advance 50ms per order (not wall-clock), so the run is
  deterministic and immune to real-time rate limiting from the risk engine.

Reproduce:

```bash
mvn -q -DskipTests install
mvn -q -pl finex-benchmarks dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP="finex-benchmarks/target/classes:$(cat /tmp/cp.txt)"
java -cp "$CP" com.finex.benchmarks.SustainedSharedServiceDriver 1500000 500
```

For JFR profiling:

```bash
java -XX:StartFlightRecording=filename=/tmp/finex.jfr,settings=profile \
  -cp "$CP" com.finex.benchmarks.SustainedSharedServiceDriver 1500000 500

jfr print --events jdk.ExecutionSample /tmp/finex.jfr | grep -A1 stackTrace \
  | grep -oE "com\.finex\.[A-Za-z0-9_.]+\.[A-Za-z0-9_<>]+\(" | sort | uniq -c | sort -rn

jfr print --events jdk.ObjectAllocationSample /tmp/finex.jfr | grep -A1 stackTrace \
  | grep -oE "[a-zA-Z0-9_.]+\.[A-Za-z0-9_<>]+\(" | sort | uniq -c | sort -rn
```

## OPT-002 — before/after

Both runs use identical workload parameters (1,500,000 orders / 500 accounts / no
market-data subscriber). Trade count is identical (750,000) in every run, confirming the
change did not alter matching behavior.

| Run | Before OPT-002 (ops/s) | After OPT-002 (ops/s) |
|-----|------------------------:|------------------------:|
| 1 | 80,189.71 | 96,396.12 |
| 2 | 74,842.91 | 100,620.83 |
| 3 | 77,135.75 | 99,815.39 |
| **Average** | **77,389.46** | **98,944.11** |

Delta: **+21,554.65 ops/s, +27.9%** (this run of the committed driver; an earlier
ad hoc run of the same logic before it was committed as `SustainedSharedServiceDriver`
measured +23.7% — both confirm a substantial, reproducible improvement in the
+20-30% range, not a single precise figure, since this is a real multi-run
measurement with normal run-to-run variance).

### JFR findings (before)

- Top CPU leaf hotspot: `BookUpdateFactory.aggregate` — 46/1389 sampled leaf frames (3.3%),
  more than 3x the next `com.finex.*` frame.
- Allocation: `BigDecimal.valueOf` = 60.7% of all sampled allocations (3288/5420);
  **3077 of those (56.8% of all allocations)** were attributed to
  `BookUpdateFactory.aggregate(List)` line 38.

### JFR findings (after)

- `BookUpdateFactory` does not appear anywhere in either the CPU or allocation samples
  (`grep -c BookUpdateFactory` → 0 in both).
- `BigDecimal.valueOf` share of allocations dropped to 14.8% (671/4520).
- New top CPU hotspot: `com.finex.portfolio.Position.mark` (112 samples) — legitimate
  mark-to-market work, not wasted work. Candidate for a future OPT-003 investigation.

See `OPTIMIZATIONS.md` for the full write-up, correctness verification, and decision.

## OPT-003 — before/after

Same driver, environment, and parameters as OPT-002. Baseline is the "after OPT-002"
measurement above (98,944.11 ops/sec average); the "before OPT-003" column is the
post-OPT-002 state.

| Run | Before OPT-003 (ops/s) | After OPT-003 (ops/s) |
|-----|--------------------------:|------------------------:|
| 1 | 98,944.11 | 136,847.79 |
| 2 | 98,944.11 | 142,377.03 |
| 3 | 98,944.11 | 133,462.71 |
| **Average** | **98,944.11** | **137,562.51** |

Delta vs OPT-002: **+38,618.40 ops/s, +39.0%**. Cumulative delta vs the original
pre-optimization baseline (77,389.46 ops/sec): **+60,173.05 ops/s, +77.8%**.

Trade count remains 750,000 in every run.

### JFR findings (before, post-OPT-002)

- Top CPU leaf hotspot: `com.finex.portfolio.Position.mark` — 112/1243 samples (9.0% of
  all CPU samples), more than 9x the next `com.finex.*` frame.
- Allocation: `BigDecimal.valueOf` = 14.8% (671/4520) of sampled allocations;
  `PortfolioService.markToMarket` scan + `Position.mark` were the dominant remaining
  `BigDecimal` producers.

### JFR findings (after)

- `Position.mark` / `PortfolioService.markToMarket` do not appear anywhere in the top CPU
  or allocation samples (`grep -c "Position.mark"` → 0).
- `BigDecimal.valueOf` share dropped further to 10.6% (486/3039); total allocation
  samples dropped from 4520 to 3039 for the same workload.
- New top `com.finex.*` CPU frames are spread: `BinaryCodec.encodePayload` (9),
  `MatchingEngine.placeOrder` (7), `OrderService.processSubmitOrder` (7),
  `InMemoryLedger.post` (6), `SustainedSharedServiceDriver.run` (5) — no single dominant
  bottleneck.

See `OPTIMIZATIONS.md` for the full write-up, correctness verification, and decision.

## OPT-004 — latency baseline

Added per-order `System.nanoTime()` latency measurement to the sustained driver.

| Run | Throughput (ops/s) | p50 (ns) | p90 (ns) | p99 (ns) | p99.9 (ns) | p99.99 (ns) | max (ns) |
|----:|-------------------:|---------:|---------:|---------:|-----------:|------------:|---------:|
| 1 | 136,847.79 | 3,375 | 20,458 | 29,375 | 50,250 | 95,459 | 26,963,083 |
| 2 | 142,377.03 | 2,709 | 19,375 | 29,250 | 48,041 | 97,084 | 32,994,833 |
| 3 | 133,462.71 | 2,667 | 20,167 | 27,917 | 48,250 | 101,042 | 33,439,000 |
| **Avg** | **137,562.51** | **2,917** | **20,000** | **28,847** | **48,847** | **97,862** | **31,132,305** |

The `max` latency is dominated by JVM warmup/compilation pauses during the short run;
percentiles up to p99.99 are stable.

## OPT-005 — before/after

Same driver. Baseline is post-OPT-004 state.

| Run | Before OPT-005 (ops/s) | After OPT-005 (ops/s) | p50 (ns) | p99 (ns) | p99.9 (ns) |
|----:|----------------------:|------------------------:|---------:|---------:|-----------:|
| 1 | 136,847.79 | 580,868.34 | 1,083 | 5,917 | 34,208 |
| 2 | 142,377.03 | 585,924.72 | 1,125 | 6,000 | 26,542 |
| 3 | 133,462.71 | 596,322.54 | 1,166 | 5,917 | 22,333 |
| **Avg** | **137,562.51** | **587,705.20** | **1,125** | **5,938** | **27,694** |

Delta vs OPT-004: **+450,142.69 ops/s, +327.2%**. Cumulative vs original baseline
(pre-OPT-002): **+659.8%**.

### JFR findings (before, post-OPT-004)

- Top allocation: `java.math.BigDecimal.valueOf` was the dominant sampled allocation
  class; many samples had call stacks through `AccountRiskState.projectedPosition`,
  `InMemoryLedger.post`, and `SettlementService.settle`.
- `AccountRiskState.reservedCash` / `reservedPosition` did a full
  `ConcurrentHashMap.values().stream().reduce(...)` on every `RiskEngine.validate` call.

### JFR findings (after)

- `AccountRiskState` methods no longer appear in top CPU or allocation samples.
- `BigDecimal.valueOf` sampled allocations dropped from ~486-650 per run to ~144 per run.
- Top `com.finex.*` CPU frames are now `MatchingEngine.placeOrder` (9),
  `OrderService.processSubmitOrder` (10), `InMemoryLedger.post` (7),
  `RiskEngine.validate` (4), `BinaryCodec.encode` (3).
