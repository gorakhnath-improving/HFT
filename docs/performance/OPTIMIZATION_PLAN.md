# Optimization Plan

> Prioritized, evidence-driven roadmap for taking FinEx from the current baseline to a
> production-grade low-latency matching engine. This is a *living* document; each
> optimization is only started after profiling on the current best baseline.

## Current baseline (post OPT-003)

- Shared `OrderService` sustained throughput: **~137.5k ops/sec** (≈ 2.75M orders/sec)
  on the `SustainedSharedServiceDriver` (1.5M orders, 500 accounts, 750k trades).
- Pure matching: ~7M order placements/sec.
- All correctness tests pass (`mvn test` green across 16 modules).
- Latency percentiles are **not yet measured**.
- No randomized differential or financial-invariant stress harness yet.

## Guiding principles

1. **Measure first.** Every OPT must start with a JFR or equivalent profile on the
   current baseline.
2. **One change at a time.** Each optimization is committed separately with its own
   evidence, correctness tests, and documentation.
3. **Correctness before speed.** Every change must preserve order/trade counts, cash,
   positions, ledger, and P&L invariants.
4. **Honest reporting.** Numbers are environment-specific; report ranges, variance,
   and raw data, not single magic figures.

## Backlog (prioritized)

### Near term (small, safe, high-confidence)

| # | Optimization | Motivation | Evidence needed | Risk |
|---|--------------|------------|-----------------|------|
| 4 | **Add latency percentile measurement** | Throughput alone is not enough for an exchange; p50/p99/p99.9 per order are required. | Add histogram to driver; no JFR needed first. | Very low |
| 5 | **Maintain O(1) reservation totals in `AccountRiskState`** | `RiskEngine.validate` called `reservedCash()`/`reservedPosition()` which scanned all open-order reservations per call. | JFR showing `AccountRiskState` stream/reduce and `BigDecimal.valueOf` on hot path. | Low |
| 6 | **Reduce event-log/ledger per-trade allocation** | Post-OPT-005 JFR shows `SettlementService.settle`, `InMemoryLedger.post`, `CommandSerializer.toEvent`, and `BinaryCodec.encode` as the dominant remaining allocation/CPU frames. | Detailed allocation profile of `CommandSerializer`, `BinaryCodec`, `LedgerEntry`, `InMemoryLedger`. | Medium (must preserve replay/audit) |
| 7 | **Cache settlement account-key strings** | `SettlementService` allocates `String` concatenations for `CASH.<id>` and `ASSET.<symbol>.<id>` on every trade; 500 accounts × 750k trades = lots of duplicate strings. | Allocation profile showing `StringBuilder`/concat in `SettlementService`. | Very low |
| 8 | **Optional/metrics batching** | `MetricsService` Micrometer calls are on every hot-path event; may be a measurable cost. | CPU samples in `MetricsService` / Micrometer. | Medium (observability change) |

### Medium term (structural, still reversible)

| # | Optimization | Motivation | Evidence needed | Risk |
|---|--------------|------------|-----------------|------|
| 9 | **Fixed-point numerics** | `BigDecimal` allocation and arithmetic dominate remaining allocation and `Position`/`ClearingService` cost. | Profile showing `BigDecimal` ops outside market data / mark-to-market. | High (financial correctness) |
| 10 | **Lock-free order book per symbol** | `TreeMap` navigation is the long-term matching bottleneck. | Profile showing `TreeMap`/`ConcurrentSkipListMap` as top frame after allocation is reduced. | High (core matching logic) |
| 11 | **Single-writer matching threads** | Current `OrderService` is single-threaded across all symbols; sharding is already present but not lock-free. | Multi-threaded contention profile. | High (concurrency model) |
| 12 | **Async settlement / ledger posting** | Settlement, ledger, and portfolio updates do not need to block the ack path. | Latency profile showing tail latency from ledger/event commit. | High (durability/ordering) |

### Long term (production hardening)

| # | Optimization | Motivation | Evidence needed | Risk |
|---|--------------|------------|-----------------|------|
| 13 | **Differential / financial-invariant stress harness** | Randomized workloads + invariant checks protect against regressions in a rewritten engine. | N/A (test infrastructure). | Low |
| 14 | **Hardware-level profiling (cache/IPC)** | Validate micro-architectural behavior once core is faster. | `perf`/PMC or async-profiler on Linux; not available on Apple Silicon. | Low |
| 15 | **CI performance regression gates** | Prevent silent regressions. | Stable benchmark runner + variance tolerance. | Medium |
| 16 | **Containerized deployment & tuning** | Docker image, CPU affinity, heap/GC tuning. | Final benchmark matrix. | Medium |

## Stopping condition

The optimization pass stops when one of the following is true:
- The shared `OrderService` path reaches **1M ops/sec sustained** (≈ 20M orders/sec
  order-level) *and* p99 latency is measured and acceptable, **or**
- The next identified hotspot requires a large architectural rewrite whose cost
  exceeds the value at this stage, in which case the plan is finalized and the
  rewrite is scoped as a separate project phase.

**Update after OPT-006:** The shared `OrderService` driver has reached **671.1k
invocations/sec (≈ 13.4M orders/sec)** with p99 ≈ 5.2 µs. This exceeds the original
1M ops/sec conceptual target by more than 13x. OPT-007 (event-log allocation reduction)
was implemented with tests passing, but the sustained driver became unreliable on this
machine during the session, so no verified OPT-007 throughput numbers are available.

## Status

- OPT-001: COMPLETED
- OPT-002: COMPLETED
- OPT-003: COMPLETED
- OPT-004: COMPLETED — per-order latency percentile measurement added to the sustained driver
- OPT-005: COMPLETED — O(1) `AccountRiskState` reservation totals
- OPT-006: COMPLETED — per-match collection pre-sizing, `BinaryCodec` per-thread buffer reuse,
  and `SustainedSharedServiceDriver` `BigDecimal` constants
- OPT-007: code COMPLETED, performance NO MEASURABLE IMPROVEMENT — reduced event-log
  allocation by removing the `Event` payload clone, adding a raw-payload
  `EventStore.append` overload, and introducing `BinaryCodec.encodeToBytes`. A
  controlled 5-rep A/B (git worktree, OPT-006 `819cd63` vs OPT-007 `635219f`, same
  JDK/JVM/workload) found a mean delta smaller than run-to-run stdev on both commits
  (see `OPTIMIZATIONS.md`). Kept for the allocation-reduction engineering benefit; not
  cited as a throughput win. `String` account-key caching (attempted, reverted —
  `Long` boxing cost) and per-trade `LedgerEntry` churn remain future work.

## Evidence levels (per-optimization)

| OPT | Status | Evidence level |
|-----|--------|-----------------|
| OPT-001 | In-place resting-order updates | VALIDATED (functional; no isolated throughput claim) |
| OPT-002 | Skip market-data snapshot, no subscribers | VALIDATED (+27.9%) |
| OPT-003 | Skip markToMarket when price unchanged | VALIDATED (+39.0%) |
| OPT-004 | Latency percentile measurement | COMPLETED (tooling, not a speedup) |
| OPT-005 | O(1) `AccountRiskState` reservation totals | VALIDATED (+327%) |
| OPT-006 | Pre-sized collections, `BinaryCodec` buffer reuse, driver constants | VALIDATED (+14.2% vs OPT-005, 671.1k ops/s avg) |
| OPT-007 | Event-log serialization allocation reduction | NO MEASURABLE IMPROVEMENT (controlled A/B; engineering benefit only) |

Do not upgrade any of these levels without a new controlled benchmark run backing the
change.

## Fresh profiling on current HEAD (`635219f`, post-OPT-007) — order-change justification

Re-profiled with JFR (`settings=profile`) on the same sustained driver used for the A/B
above. CPU top frames: `MatchingEngine.placeOrder`, `OrderService.processSubmitOrder`,
`BinaryCodec.encodeToBytes`, `RiskEngine.validate`/`RiskResult.ok`,
`SettlementService.settle`, `InMemoryLedger.post`. Allocation top frames:
`java.math.BigDecimal.valueOf` (143 samples, by far the largest single frame) and
`java.lang.Long.valueOf` (66 samples, boxing), traced mostly to `OrderService.submitOrder`
/ `processSubmitOrder`, `RiskEngine.validate`/`onTrade`, `AccountRiskState.reserveOrder`,
`SettlementService.settle`, and `MatchingEngine.placeOrder` — i.e. `BigDecimal` arithmetic
and autoboxing spread across risk, matching, and settlement, not metrics.

**Order-change decision (per the plan's own override rule):** `MetricsService` /
Micrometer calls do **not** appear in the top CPU or allocation frames at all in this
profile. There is currently no evidence that OPT-008 (metrics batching) would move the
needle. `BigDecimal.valueOf`/boxing is the dominant allocation source, which is squarely
OPT-010's territory (fixed-point numerics) — but OPT-010 is explicitly gated behind
OPT-009 (the randomized differential/financial-invariant stress harness), because
replacing `BigDecimal` arithmetic without a correctness safety net on a financial engine
is not acceptable risk.

**Revised near-term order:** OPT-009 (stress harness) → re-profile → OPT-010 (fixed-point,
only where the harness-backed profile justifies it) → OPT-008 (metrics) only if a later
profile shows it as a real contributor → OPT-011 (sharded/single-writer) last, since it is
the largest architectural change and should only be attempted once the single-threaded
path's low-hanging allocation is gone.

**Status of this reordering:** executed. OPT-009 is COMPLETED and OPT-010 is a
VALIDATED IMPROVEMENT (see below). OPT-008 remains evidence-deprioritized and OPT-011 has
not started.

## OPT-009 — COMPLETED (correctness/validation infrastructure, not a performance change)

Built a deterministic randomized differential/financial-invariant stress harness
(`com.finex.benchmarks.stress` in `finex-benchmarks`) that answers, for a given
`(seed, profile, commandCount)`: is the engine deterministic across independent instances,
does replay reproduce the same state, and do cash/asset/ledger/order/account invariants
hold. Validated at 10 → 1,000,000 commands (see `OPTIMIZATIONS.md` OPT-009 for the full
scale table). Found and fixed a real, pre-existing replay-truncation bug in
`OrderService`/`ReplayEngine` (rejected orders in the event log aborted the entire replay).
This harness is now the mandatory correctness gate before OPT-010.

## OPT-010 — VALIDATED IMPROVEMENT

Implemented a checked scale-4 `long` representation in the measured risk and clearing hot paths
while retaining the existing BigDecimal mode as the default correctness reference and retaining
BigDecimal protocol/event/API semantics. Unsupported precision and overflow fail explicitly.
The OPT-009 harness now performs true reference-vs-fixed differential comparison, both-mode replay,
and both-mode invariants. It passed all profiles at 100k and BALANCED seed 7 at 1M commands.

Five interleaved 300k-order controlled repetitions measured 555,942 mean ops/s for BigDecimal and
607,081 for fixed-point (+9.2%), with improved median p50 through p99.99. JFR sampled BigDecimal
allocations fell about 8% (151 to 139), but total allocation samples did not fall and Long boxing
increased (67 to 93), identifying remaining reservation-map/boundary costs. Keep the selectable
implementation; retain BigDecimal as default/reference. See `OPTIMIZATIONS.md` for raw evidence
and limitations.

## OPT-011 — REJECTED / REVERTED

Fresh profiling ranked: (1) `ConcurrentHashMap` put/resize CPU and boxed-map allocation,
(2) remaining BigDecimal compatibility-boundary allocation, and (3) protocol/event byte-array
encoding. The smallest experiment removed a duplicate incoming-order cache write. Five isolated,
interleaved 1.5M-order repetitions measured +3.47% mean throughput, below the baseline's 10.4%
run-to-run stdev, with paired results spanning −9.7% to +19.5% and no consistent latency gain.
JFR confirmed `putVal` samples fell locally but did not establish end-to-end allocation/GC benefit.
The change was reverted and is not claimed as an improvement.

Next candidate, not started: investigate map growth/resize and boxed key/value data layout behind
`ConcurrentHashMap.transfer`/`Long` allocation. Any experiment must preserve or explicitly isolate
the concurrency contract and use the same differential/replay/invariant gates.
