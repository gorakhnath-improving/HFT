# Failure Testing

Phase 21 adds chaos-style and negative-path coverage to complement the happy-path suites.

## Invalid input (`OrderServiceFailureTest`)

- `null` request
- Blank or missing symbol
- Non-positive quantity
- `LIMIT` order without a positive price
- `MARKET` order supplied with a price
- Order whose notional exceeds the configured `maxOrderNotional`

Expected behavior: `IllegalArgumentException` for structural problems, `OrderRejectedException`
for business-rule rejections.

## Event-store corruption (`EventStoreFailureTest`)

- Append an event with an unknown `type` (e.g. `"UNKNOWN"`) to the `InMemoryEventStore`.
- `OrderService.replay()` must throw `IllegalArgumentException` with message
  `"unknown event type"` instead of silently skipping or creating undefined state.

This guards against future schema changes: any new event type must be handled in
`ReplayEngine` and `CommandSerializer`.

## Restart and replay

`OrderServiceReplayTest` already exercises a restart-equivalent scenario: after a
sequence of orders, a new `OrderService` is created over the same `EventStore`, replayed,
and the reconstructed ledger and portfolio are verified against the originals.

## Future work

- Duplicate client order id detection
- Out-of-sequence / backdated events
- Partial event-store truncation and snapshot + replay
- Network partitions between API and database (once multi-node deployment is added)
