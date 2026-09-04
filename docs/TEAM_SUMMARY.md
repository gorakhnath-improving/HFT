# FinEx — System Design & Performance Engineering Study Project

**Repo:** https://github.com/gorakhnath-improving/HFT

## What this is

**FinEx** is a simulated financial exchange (order book, matching engine, risk engine,
clearing, settlement, double-entry ledger) built as a **study/practice project for system
design of fast systems** — specifically, the discipline used in real low-latency/HFT-style
engineering: measure first, optimize only what's proven slow, validate every change with a
controlled experiment, and never sacrifice correctness for speed.

The goal was not to build a real exchange. It was to practice the *methodology*: take a
correct-but-slow baseline and make it measurably faster, with evidence for every claim.

## What was built

- **16-module Java / Spring Boot system**: order book, matching engine, risk engine, clearing,
  settlement, double-entry ledger, portfolio/P&L, market data, a custom binary wire protocol,
  an append-only event log with replay, REST API, metrics, API-key security.
- **A correctness safety net, built *before* touching performance-sensitive code**: a
  randomized differential-testing harness that generates random order/cancel sequences across
  7 workload profiles and checks, on every run:
  - Two independently constructed engine instances produce identical results (determinism)
  - Replaying the event log reproduces the exact same final state (event-sourcing correctness)
  - Five financial invariants always hold: cash conservation, asset conservation, ledger
    double-entry balance, order-quantity conservation, account isolation
  - Validated up to **1,000,000 generated commands**.
- **A selectable fixed-point numeric engine** as an alternative to `BigDecimal` for the hot
  path, proven byte-for-byte equivalent to the reference implementation *before* it was ever
  benchmarked for speed.

## How decisions were made

One rule was enforced for every single change, no exceptions:

> **measure → hypothesize → experiment → validate → document**

Concretely:

1. **Never optimize from intuition.** Every optimization started from a JFR (JDK Flight
   Recorder) profile of the *current* code showing an actual CPU or allocation hotspot — never
   "this looks like it should be slow."
2. **Never trust a single benchmark run.** Every accepted optimization was validated with a
   **controlled, interleaved A/B test**: same JVM, same workload, alternating baseline/candidate
   runs 5-10 times, comparing mean, median, and standard deviation — because run-to-run
   variance on a shared machine was measured as high as 2-3x from background load alone.
3. **Correctness gates come before performance gates.** No numeric or data-structure change was
   accepted until it passed the full differential/replay/invariant suite.
4. **Negative results are results, not failures to hide.** Two proposed optimizations were
   explicitly rejected because the evidence didn't support them — see below.
5. **Stop when done; don't chase vanity numbers.** The project deliberately stopped at a
   strong, honestly-measured result instead of endlessly optimizing toward "1M ops/sec" for
   its own sake.

## The optimization history (13 numbered experiments)

| # | What was tried | Result |
|---|---|---|
| OPT-001 | In-place order-book updates instead of remove/reinsert | Completed |
| OPT-002 | Skip market-data snapshot work when nobody's subscribed | **+24–28%** |
| OPT-003 | Skip mark-to-market recompute when price hasn't changed | **+39%** |
| OPT-004 | Added latency percentile measurement (p50/p99/p99.9) | Tooling, not a speedup |
| OPT-005 | Replaced O(n) reservation-map scan with running totals | **+327%** (largest single win) |
| OPT-006 | Removed redundant collection copies + buffer reuse | **+14.2%** |
| OPT-007 | Reduced event-log allocation | Code kept, **A/B showed no measurable speedup** — reported honestly |
| OPT-008 | Considered batching metrics calls | **Never done** — profiling never showed metrics as a bottleneck |
| OPT-009 | Built the correctness/differential test harness | Infrastructure, not a speedup — but *mandatory* before OPT-010 |
| OPT-010 | Fixed-point arithmetic on the risk/clearing hot path (opt-in; `BigDecimal` stays default) | **+9.2%** |
| OPT-011 | Tried removing a duplicate cache write | **Rejected** — result was inside benchmark noise, reverted |
| OPT-012 | Replaced boxed `ConcurrentHashMap<Long,Long>` with a primitive open-addressed map | **+5.09%** |
| OPT-013 | Consolidated three separate reservation maps into one | **+13.97%**, with a documented tail-latency tradeoff (see below) |

## Actual test results — the honest numbers

**Best controlled result** (OPT-013, five paired interleaved runs, same machine/JVM):

- Baseline: 829,499 mean ops/sec
- Optimized: **945,413 mean ops/sec** (+13.97%)
- Latency p50 through p99.99 all improved; **maximum** latency slightly regressed — we
  documented this tradeoff rather than hiding it.

**The uncomfortable but important part:** when the same exact code was re-run later for the
final report, on a machine under heavier background load, it measured only **~404,000
ops/sec** — less than half. This was not hidden or re-run repeatedly until it "looked good."
It's the same code, same methodology, just a noisier machine at that moment. This is *why*
every accepted result used paired A/B comparisons instead of trusting any single absolute
number — a single benchmark run on shared hardware can be off by 2-3x for reasons that have
nothing to do with the code.

**Correctness (never regressed once):**

- `mvn test` green across all 16 modules throughout the entire project
- All 7 randomized workload profiles passed at 100,000 commands; one profile validated to
  1,000,000 commands
- Fixed-point vs. `BigDecimal` produced byte-identical results on every validated run

## Key takeaways

1. A benchmark number without a controlled comparison methodology is close to meaningless on
   shared hardware — build the A/B discipline in from day one.
2. Correctness infrastructure (the differential harness, OPT-009) is what *unlocked* the
   riskiest optimization (fixed-point arithmetic, OPT-010) — it would not have been safe to
   attempt OPT-010 without OPT-009 existing first.
3. Being willing to say "this didn't work" (OPT-007, OPT-011) is what makes the positive
   results credible.
4. The biggest wins came from **removing accidental work** (OPT-002, OPT-003, OPT-005), not
   exotic data structures — profile first, always.

## Where to look for more detail

- [`docs/performance/OPTIMIZATION_JOURNEY.md`](performance/OPTIMIZATION_JOURNEY.md) — the full
  narrative of the optimization arc
- [`docs/performance/OPTIMIZATIONS.md`](performance/OPTIMIZATIONS.md) — per-experiment
  technical detail with raw numbers
- [`docs/performance/FINAL_BENCHMARK_REPORT.md`](performance/FINAL_BENCHMARK_REPORT.md) — final
  honest benchmark status, including the machine-load variance case
- [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) — system diagram and module boundaries
- [`docs/FUTURE_RESEARCH.md`](FUTURE_RESEARCH.md) — evidence-ranked directions deliberately
  not pursued, and why
