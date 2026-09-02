# TODO — Active Task Queue

## Phase 9 — Event Architecture (done)

- [x] `finex-event-log` module, event store, replay

## Phase 10 — Symbol Sharding (in progress)

Goal: Multiple independent matching engine shards; scaling measurements.
- [ ] Decide: shard abstraction in `finex-api` vs new `finex-shard` module
- [ ] `SymbolShardRouter` mapping symbol -> shard id (consistent hash / modulo)
- [ ] `EngineShard` owning one `MatchingEngine`, one `OrderService`-equivalent state
- [ ] `OrderService` routes submit/cancel to shard and aggregates book snapshots
- [ ] Sharding correctness test (orders for different symbols go to different engines)
- [ ] `mvn test` green + commit

## Phase 11 — Ledger (pending)

Goal: Double-entry ledger, immutable entries, debits == credits invariant.
