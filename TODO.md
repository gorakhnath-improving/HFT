# TODO — Active Task Queue

## Phase 7 — Market Data (done)

- [x] `finex-market-data` module and integration

## Phase 8 — Binary Protocol (done)

- [x] `finex-protocol` module and binary codec

## Phase 9 — Event Architecture (in progress)

Goal: Append-only event log powering replay.
- [ ] Create `finex-event-log` Maven module
- [ ] Define `Event` record (id, timestamp, type, payload bytes)
- [ ] `EventStore` interface: `append(Event)`, `readAll()`, `replay(ReplayHandler)`
- [ ] In-memory `InMemoryEventStore` baseline
- [ ] Wire `OrderService` to append `SubmitOrder`, `CancelOrder`, `MatchResult` events
- [ ] `ReplayEngine` reconstructs `OrderService` state from event log
- [ ] `EventStoreTest` / `ReplayTest`
- [ ] `mvn test` green + commit

## Phase 10 — Symbol Sharding (pending)

Goal: Multiple independent matching engine shards; scaling measurements.

## Phase 11 — Ledger (pending)

Goal: Double-entry ledger, immutable entries, debits == credits invariant.
