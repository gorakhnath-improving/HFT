# AGENT CONTEXT (keep short)

**Current phase:** Phase 17 — Performance Benchmarks (batched: Phases 17-22 requested)
**Current task:** Add JMH/component/end-to-end benchmark module.

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
- (in progress) `finex-benchmarks` for JMH/component/end-to-end benchmarks

**Completed milestones:**
- Phases 1-16 committed.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A (being set up).

**Last successful build:** `mvn test` green after Phase 16.

**Next action:** Create `finex-benchmarks` module with JMH plugin and baseline benchmarks.

**Important commands:**
```bash
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
mvn -pl finex-benchmarks exec:java     # run JMH benchmarks (after setup)
```
