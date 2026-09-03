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
- [ ] OPT-009 (do next): Add randomized differential/financial-invariant stress harness.
  Reordered ahead of OPT-008/010 because fresh JFR profiling shows `BigDecimal`/boxing
  (not metrics) dominating allocation, and changing numeric representation safely
  requires this harness first.
- [ ] OPT-010: Investigate fixed-point numerics for the hot path — gated behind OPT-009.
- [ ] OPT-008: Investigate metrics offloading/batching — deprioritized; `MetricsService`
  does not appear in current top CPU/allocation frames. Revisit only if a future profile
  supports it.
- [ ] OPT-011: Investigate lock-free or single-writer order book per symbol shard — last,
  after single-threaded allocation work is exhausted.
