# TODO — Active Task Queue

## Completed

- [x] Phases 1-24: all project plan phases implemented, documented, benchmarked, and tested.
- [x] OPT-002: skip market-data snapshot construction with zero subscribers.
- [x] OPT-003: skip `markToMarket` when the mark price has not changed.
- [x] OPT-004: add per-order latency percentile measurement to `SustainedSharedServiceDriver`.
- [x] OPT-005: maintain O(1) reservation totals in `AccountRiskState`.
- [x] OPT-006: reduce per-match collection copies and encode-buffer allocation.
- [x] Full `mvn test` green after OPT-006.

## Performance-engineering backlog (not yet in master plan)

- [ ] OPT-007: further event-log/ledger allocation reduction (`Event` copy in
  `InMemoryEventStore.append`, per-trade `LedgerEntry` creation, `String` account-key
  caching — top remaining CPU/allocation frames after OPT-006).
- [ ] Investigate metrics offloading/batching (`MetricsService` is still on the hot path).
- [ ] Add randomized differential/financial-invariant stress harness.
- [ ] Investigate fixed-point numerics for the hot path.
- [ ] Investigate lock-free or single-writer order book per symbol shard.
