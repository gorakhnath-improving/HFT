# AGENT CONTEXT (keep short)

**Current phase:** Phase 16 — Load Generator (batched: Phases 12-16 completed)
**Current task:** Update project docs and perform final commit(s).

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
- `finex-clearing` — trade clearing and fees
- `finex-settlement` — settlement orchestration
- `finex-load-generator` — configurable load generator
- `finex-api` — `OrderService` + controllers

**Completed milestones:**
- Phases 1-16 completed and committed.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn test` green after Phase 16.

**Next action:** Update `PROJECT_PLAN.md`, `PROGRESS.md`, `README.md`, `TODO.md`, `DESIGN_DECISIONS.md`
and commit.

**Important commands:**
```bash
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
