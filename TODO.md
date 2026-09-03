# TODO — Active Task Queue

## Completed

- [x] Phases 1-24: all project plan phases implemented, documented, benchmarked, and tested.
- [x] OPT-002: skip market-data snapshot construction with zero subscribers.
- [x] OPT-003: skip `markToMarket` when the mark price has not changed.
- [x] OPT-004: add per-order latency percentile measurement to `SustainedSharedServiceDriver`.
- [x] OPT-005: maintain O(1) reservation totals in `AccountRiskState`.
- [x] Full `mvn test` green after OPT-005.

## Performance-engineering backlog (not yet in master plan)

- [ ] OPT-006: event-log/ledger allocation reduction (`SettlementService.settle`,
  `InMemoryLedger.post`, `CommandSerializer.toEvent`, `BinaryCodec.encode` now top
  remaining CPU/allocation frames after OPT-005).
- [ ] Cache settlement account-key strings (`CASH.<id>`, `ASSET.<symbol>.<id>`) to reduce
  per-trade `StringBuilder` allocation.
- [ ] Investigate metrics offloading/batching (`MetricsService` is still on the hot path).
- [ ] Add randomized differential/financial-invariant stress harness.
- [ ] Investigate fixed-point numerics for the hot path.
- [ ] Investigate lock-free or single-writer order book per symbol shard.
