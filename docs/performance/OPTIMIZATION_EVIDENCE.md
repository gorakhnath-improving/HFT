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
