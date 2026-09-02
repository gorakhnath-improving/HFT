# AGENT CONTEXT (keep short)

**Current phase:** Phase 11 — Ledger (batched: Phases 7-11 requested)
**Current task:** Add double-entry ledger, immutable entries, and invariant that debits equal credits.

**Architecture (current):** Maven multi-module reactor.
- `finex-common` — domain model
- `finex-order-book` — `OrderBook`
- `finex-matching-engine` — `MatchingEngine`, `MatchResult`, `Trade`
- `finex-risk` — `RiskEngine`
- `finex-market-data` — market-data events
- `finex-protocol` — binary codec
- `finex-event-log` — append-only events, replay
- `finex-shard` — `ShardCoordinator`, `EngineShard`, `SymbolShardRouter`
- `finex-api` — `OrderService` + `OrderController`

**Completed milestones:**
- Phases 1-10 committed or in progress.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs. ADR-000 through ADR-005.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn test` green after Phase 10.

**Next action:** Create `finex-ledger` module with `Ledger`, `LedgerAccount`, `LedgerEntry`,
`EntryType`, and integrate trade postings into `OrderService`.

**Important commands:**
```bash
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
