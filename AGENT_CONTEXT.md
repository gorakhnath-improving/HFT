# AGENT CONTEXT (keep short)

**Current phase:** Phase 13 — Clearing (batched: Phases 12-16 requested)
**Current task:** Compute buyer/seller obligations, fees, and net cash/asset transfers per trade.

**Architecture (current):** Maven multi-module reactor.
- `finex-common` — domain model
- `finex-order-book` — `OrderBook`
- `finex-matching-engine` — `MatchingEngine`, `MatchResult`, `Trade`
- `finex-risk` — `RiskEngine`
- `finex-market-data` — market-data events
- `finex-protocol` — binary codec
- `finex-event-log` — append-only events, replay
- `finex-shard` — symbol sharding
- `finex-ledger` — double-entry ledger
- `finex-portfolio` — positions and P&L
- `finex-api` — `OrderService` + `OrderController` + `PortfolioController`
- (in progress) `finex-clearing` for obligations and fees

**Completed milestones:**
- Phases 1-12 committed.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn test` green after Phase 12.

**Next action:** Create `finex-clearing` module with `FeeSchedule`, `ClearingResult`, and
`ClearingService`; integrate fee ledger entries into `OrderService`.

**Important commands:**
```bash
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
