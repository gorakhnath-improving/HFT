# TODO — Active Task Queue

## Completed

- [x] Phases 1-24: all project plan phases implemented, documented, benchmarked, and tested.
- [x] OPT-002: skip market-data snapshot construction with zero subscribers.
- [x] OPT-003: skip `markToMarket` when the mark price has not changed.
- [x] OPT-004: add per-order latency percentile measurement to `SustainedSharedServiceDriver`.
- [x] OPT-005: maintain O(1) reservation totals in `AccountRiskState`.
- [x] OPT-006: reduce per-match collection copies and encode-buffer allocation.
- [x] Full `mvn test` green after OPT-006.

## Performance-engineering backlog

- [x] OPT-007: further event-log/ledger allocation reduction (`Event` copy in
  `InMemoryEventStore.append`, `BinaryCodec` `ByteBuffer` intermediate allocation,
  `CommandSerializer.toEvent` temporary `Event`). Implemented and tests pass. Controlled
  5-rep A/B (git worktree vs OPT-006) found **NO MEASURABLE IMPROVEMENT** — kept for the
  allocation-reduction engineering benefit only; not a validated speedup.
- [x] OPT-009: Add randomized differential/financial-invariant stress harness
  (`com.finex.benchmarks.stress`). Validated deterministic/replay/invariant correctness
  from 10 to 1,000,000 generated commands across 7 workload profiles. Found and fixed a
  real pre-existing replay-truncation bug (rejected orders aborted `ReplayEngine`).
  Classified as correctness/validation infrastructure, not a performance change.
- [ ] OPT-010 (do next): Investigate fixed-point numerics for the hot path, using the
  OPT-009 harness as the correctness oracle. Motivated by JFR profiling showing
  `BigDecimal.valueOf`/boxing dominating allocation in risk/matching/settlement.
- [ ] OPT-008: Investigate metrics offloading/batching — deprioritized; `MetricsService`
  does not appear in current top CPU/allocation frames. Revisit only if a future profile
  supports it.
- [ ] OPT-011: Investigate lock-free or single-writer order book per symbol shard — last,
  after single-threaded allocation work is exhausted.
