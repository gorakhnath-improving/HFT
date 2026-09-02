# AGENT CONTEXT (keep short)

**Current phase:** Phase 10 — Symbol Sharding (batched: Phases 7-11 requested)
**Current task:** Add multiple independent matching-engine shards and route symbols to shards;
measure sharding correctness.

**Architecture (current):** Maven multi-module reactor.
- `finex-common` — domain model
- `finex-order-book` — `OrderBook`
- `finex-matching-engine` — `MatchingEngine`, `MatchResult`, `Trade`
- `finex-risk` — `RiskEngine`
- `finex-market-data` — market-data events
- `finex-protocol` — binary codec
- `finex-event-log` — append-only events, replay
- `finex-api` — `OrderService` + `OrderController`

**Completed milestones:**
- Phases 1-9 committed or in progress.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs. ADR-000 through ADR-005.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn test` green after Phase 9 event-log integration.

**Next action:** Implement `EngineShard` and `SymbolShardRouter` in `finex-api` (or a new
`finex-shard` module if appropriate); update `OrderService` to route by symbol.

**Important commands:**
```bash
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
