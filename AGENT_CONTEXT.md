# AGENT CONTEXT (keep short)

**Current phase:** Phase 11 — Ledger (done; finalizing batched Phases 7-11)
**Current task:** Update remaining project docs and commit Phase 11.

**Architecture (current):** Maven multi-module reactor.
- `finex-common` — domain model
- `finex-order-book` — `OrderBook`
- `finex-matching-engine` — `MatchingEngine`, `MatchResult`, `Trade`
- `finex-risk` — `RiskEngine`
- `finex-market-data` — market-data events
- `finex-protocol` — binary codec
- `finex-event-log` — append-only events, replay
- `finex-shard` — `ShardCoordinator`, `EngineShard`, `SymbolShardRouter`
- `finex-ledger` — double-entry ledger
- `finex-api` — `OrderService` + `OrderController`

**Completed milestones:**
- Phases 1-11 committed or ready for commit.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs. ADR-000 through ADR-005 plus
ADR-006 (sharding) and ADR-007 (ledger) should be added to document the new modules.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn test` green after Phase 11.

**Next action:** Final commit(s) for Phase 11 and optional ADRs.

**Important commands:**
```bash
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
