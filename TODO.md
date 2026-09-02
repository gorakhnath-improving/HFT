# TODO — Active Task Queue

## Phase 12 — Portfolio / P&L (done)

- [x] `finex-portfolio` module, `PortfolioService`, `PortfolioController`, tests

## Phase 13 — Clearing (in progress)

Goal: Buyer/seller obligations, fees, asset/cash movement determination.
- [ ] Create `finex-clearing` Maven module
- [ ] `FeeSchedule` with taker/maker rates
- [ ] `ClearingResult` with net cash/asset for buyer/seller and fee
- [ ] `ClearingService` computing obligations per trade
- [ ] `OrderService` posts fee entry to ledger and adjusts cash in portfolio
- [ ] `ClearingServiceTest`, `OrderServiceClearingTest`
- [ ] `mvn test` green + commit

## Phase 14 — Settlement (pending)

Goal: Simulated settlement lifecycle updating cash/assets/positions/ledger.

## Phase 15 — Replay (pending)

Goal: Replay event log, verify reconstructed state == original state.

## Phase 16 — Load Generator (pending)

Goal: Dedicated Java load generator with configurable workloads.

## Final

- [ ] Full `mvn test` green
- [ ] Update docs and commit
