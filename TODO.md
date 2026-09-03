# TODO — Active Task Queue

**Status: FINALIZED / PORTFOLIO COMPLETE.** There is no active task queue. All planned phases
and all authorized performance-engineering optimizations are complete and validated. This file
is kept for historical record; do not add new work items here without an explicit new user
request. Future optimization ideas belong in [`docs/FUTURE_RESEARCH.md`](docs/FUTURE_RESEARCH.md)
as research directions, not as TODOs.

## Completed

- [x] Phases 1-24: all project plan phases implemented, documented, benchmarked, and tested.
- [x] OPT-002: skip market-data snapshot construction with zero subscribers.
- [x] OPT-003: skip `markToMarket` when the mark price has not changed.
- [x] OPT-004: add per-order latency percentile measurement to `SustainedSharedServiceDriver`.
- [x] OPT-005: maintain O(1) reservation totals in `AccountRiskState`.
- [x] OPT-006: reduce per-match collection copies and encode-buffer allocation.
- [x] Full `mvn test` green after OPT-006.

## Performance-engineering backlog (complete)

- [x] OPT-007: further event-log/ledger allocation reduction (`Event` copy in
  `InMemoryEventStore.append`, `BinaryCodec` `ByteBuffer` intermediate allocation,
  `CommandSerializer.toEvent` temporary `Event`). Implemented and tests pass. Controlled
  5-rep A/B (git worktree vs OPT-006) found **NO MEASURABLE IMPROVEMENT** — kept for the
  allocation-reduction engineering benefit only; not a validated speedup.
- [x] OPT-008: Investigate metrics offloading/batching — **deferred**; `MetricsService` never
  appeared in any top CPU/allocation frame across the entire project. Not implemented.
- [x] OPT-009: Add randomized differential/financial-invariant stress harness
  (`com.finex.benchmarks.stress`). Validated deterministic/replay/invariant correctness
  from 10 to 1,000,000 generated commands across 7 workload profiles. Found and fixed a
  real pre-existing replay-truncation bug (rejected orders aborted `ReplayEngine`).
  Classified as correctness/validation infrastructure, not a performance change.
- [x] OPT-010: Checked scale-4 fixed-point numerics integrated into risk and clearing with
  BigDecimal retained as default/reference. True differential/replay/invariant stress passed
  through 1M commands. Controlled 5-rep A/B measured +9.2% mean throughput with improved
  median p50–p99.99; JFR BigDecimal samples fell 151→139 but total samples were unchanged.
  **VALIDATED IMPROVEMENT** for representable scale-4 workloads.
- [x] OPT-011: Remove redundant incoming-order cache write. Controlled A/B measured +3.47%
  mean with paired results from −9.7% to +19.5%, inside baseline variation; no consistent
  latency/allocation improvement. **REJECTED / REVERTED — NO MEASURABLE IMPROVEMENT.**
- [x] OPT-012: Primitive long-to-long reservation maps inside owner-serialized fixed-point risk
  state. Ten-pair A/B: +5.09% mean throughput; Long allocation pressure 24.84%→11.26%; full
  differential/replay/invariant gates pass. **VALIDATED IMPROVEMENT / KEPT.**
- [x] OPT-013: Consolidate three same-key primitive reservation maps into one parallel-value
  table. Five-pair A/B: +13.97% mean throughput; median p50–p99.99 improved, max/GC pause
  regressed and documented. **VALIDATED IMPROVEMENT / KEPT.**

## Finalization (complete)

- [x] Full `mvn test` re-verified green across all 16 modules immediately before finalization.
- [x] Final benchmark reproducibility run recorded honestly, including a documented
  machine-load-driven variance (see `docs/performance/FINAL_BENCHMARK_REPORT.md`).
- [x] README polished with honest positioning, architecture summary, and design highlights.
- [x] `docs/ARCHITECTURE.md` updated with a control-plane/trading-plane system diagram.
- [x] `docs/performance/OPTIMIZATION_JOURNEY.md` written (narrative of measure → hypothesize →
  experiment → validate → document across all 13 optimizations, including the two rejected).
- [x] `docs/FUTURE_RESEARCH.md` created, separating 8 evidence-ranked future directions from
  current work.
- [x] Project-state files (`AGENT_CONTEXT.md`, `PROGRESS.md`, this file) updated to
  **FINALIZED / PORTFOLIO COMPLETE**.
- [x] Repository cleanliness verified: no secrets, no stray artifacts, `.gitignore` adequate,
  working tree clean.

## Not started (see `docs/FUTURE_RESEARCH.md` — deliberately not authorized work)

1. Protocol/event byte-array allocation optimization
2. Ledger-entry allocation optimization
3. GC/tail-latency investigation on an isolated benchmarking host
4. Matching-engine structural optimization (order-book data structure)
5. Remaining `BigDecimal` boundary analysis (portfolio, ledger, matching)
6. Concurrency-contract refinement for service-level maps
7. Single-writer / sharded matching architecture
8. Metrics batching (only if a future profile changes the current evidence)
