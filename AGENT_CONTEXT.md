# AGENT CONTEXT (keep short)

**Current phase:** Phase 9 — Event Architecture (batched: Phases 7-11 requested)
**Current task:** Append-only event log that records commands and results and can replay
state reconstruction.

**Architecture (current):** Maven multi-module reactor.
- `finex-common` — domain model
- `finex-order-book` — `OrderBook`
- `finex-matching-engine` — `MatchResult`, `Trade`
- `finex-risk` — `RiskEngine`
- `finex-market-data` — market-data events
- `finex-protocol` — binary protocol messages and `BinaryCodec`
- `finex-api` — `OrderService` + `OrderController`
- (in progress) `finex-event-log` for append-only events and replay

**Completed milestones:**
- Phases 1-8 committed or in progress.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs. ADR-000 through ADR-005.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn test` green after Phase 8 binary codec.

**Next action:** Create `finex-event-log` module, define `Event` and `EventStore`, and wire
`OrderService` to append commands/results; implement replay test.

**Important commands:**
```bash
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
