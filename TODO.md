# TODO — Active Task Queue

## Completed

- [x] Phases 1-24: all project plan phases implemented, documented, benchmarked, and tested.
- [x] OPT-002: skip market-data snapshot construction with zero subscribers.
- [x] OPT-003: skip `markToMarket` when the mark price has not changed.
- [x] Full `mvn test` green after OPT-003.

## Performance-engineering backlog (not yet in master plan)

- [ ] Profile and tackle next hotspot (post-OPT-003 JFR shows `BinaryCodec.encodePayload`,
  `MatchingEngine.placeOrder`, `InMemoryLedger.post`, `SettlementService.settle` as the
  top `com.finex.*` CPU frames; allocation dominated by per-trade `Event`/`LedgerEntry`
  objects and `BigDecimal.valueOf`).
- [ ] Add `p50/p90/p99/p99.9` latency histogram benchmark (not only throughput).
- [ ] Add randomized differential/financial-invariant stress harness.
- [ ] Investigate fixed-point numerics for the hot path.
- [ ] Investigate lock-free or single-writer order book per symbol shard.
