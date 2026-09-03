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
- [x] OPT-010: Checked scale-4 fixed-point numerics integrated into risk and clearing with
  BigDecimal retained as default/reference. True differential/replay/invariant stress passed
  through 1M commands. Controlled 5-rep A/B measured +9.2% mean throughput with improved
  median p50–p99.99; JFR BigDecimal samples fell 151→139 but total samples were unchanged.
  **VALIDATED IMPROVEMENT** for representable scale-4 workloads.
- [ ] OPT-008: Investigate metrics offloading/batching — deprioritized; `MetricsService`
  does not appear in current top CPU/allocation frames. Revisit only if a future profile
  supports it.
- [x] OPT-011: Remove redundant incoming-order cache write. Controlled A/B measured +3.47%
  mean with paired results from −9.7% to +19.5%, inside baseline variation; no consistent
  latency/allocation improvement. **REJECTED / REVERTED — NO MEASURABLE IMPROVEMENT.**
- [x] OPT-012: Primitive long-to-long reservation maps inside owner-serialized fixed-point risk
  state. Ten-pair A/B: +5.09% mean throughput; Long allocation pressure 24.84%→11.26%; full
  differential/replay/invariant gates pass. **VALIDATED IMPROVEMENT / KEPT.**
- [x] OPT-013: Consolidate three same-key primitive reservation maps into one parallel-value
  table. Five-pair A/B: +13.97% mean throughput; median p50–p99.99 improved, max/GC pause
  regressed and documented. **VALIDATED IMPROVEMENT / KEPT.**
- [ ] Next candidate: protocol/event encoding and byte-array allocation, preserving byte-identical
  event compatibility. Not started.
