# TODO — Active Task Queue

## Phase 7 — Market Data (done)

- [x] `finex-market-data` module
- [x] Events and publisher wired into `OrderService`
- [x] Tests + commit

## Phase 8 — Binary Protocol (in progress)

Goal: Compact binary trading protocol (NEW_ORDER/CANCEL/MODIFY, ACK/REJECT/EXECUTION).
- [ ] Create `finex-protocol` Maven module
- [ ] Define message types and `ProtocolMessage` sealed hierarchy
- [ ] Implement `BinaryCodec` (encode/decode to/from `ByteBuffer`)
- [ ] `NEW_ORDER`, `CANCEL_ORDER`, `MODIFY_ORDER` request messages
- [ ] `ORDER_ACK`, `ORDER_REJECTED`, `EXECUTION` response messages
- [ ] `ProtocolCodecTest`
- [ ] `mvn test` green + commit

## Phase 9 — Event Architecture (pending)

Goal: Append-only event log powering replay.

## Phase 10 — Symbol Sharding (pending)

Goal: Multiple independent matching engine shards; scaling measurements.

## Phase 11 — Ledger (pending)

Goal: Double-entry ledger, immutable entries, debits == credits invariant.
