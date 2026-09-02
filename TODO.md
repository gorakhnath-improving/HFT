# TODO — Active Task Queue

## Phase 10 — Symbol Sharding (done)

- [x] `finex-shard` module and `OrderService` routing

## Phase 11 — Ledger (in progress)

Goal: Double-entry ledger, immutable entries, debits == credits invariant.
- [ ] Create `finex-ledger` Maven module
- [ ] `LedgerAccount` (code, type: asset/liability/equity/revenue/expense)
- [ ] `LedgerEntry` immutable (id, timestamp, account, debit/credit, amount, currency, narration)
- [ ] `Ledger` interface: `post(List<LedgerEntry>)`, `balance(account)`, `entries()`
- [ ] In-memory `InMemoryLedger` enforcing `sum(debits) == sum(credits)` per post
- [ ] `OrderService` posts cash/asset transfers on trade (buyer cash credit, asset debit, etc.)
- [ ] `LedgerTest` verifying double-entry invariants and trade postings
- [ ] `mvn test` green + commit

## Final

- [ ] Full `mvn test`
- [ ] Commit Phase 11
- [ ] Update `README.md` / `DESIGN_DECISIONS.md` / `PROJECT_PLAN.md` / `PROGRESS.md` if needed
