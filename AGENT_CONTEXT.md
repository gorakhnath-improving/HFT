# AGENT CONTEXT (keep short)

**Current phase:** Phase 8 — Binary Protocol (batched: Phases 7-11 requested)
**Current task:** Define compact binary trading protocol messages (NEW_ORDER/CANCEL/MODIFY,
ACK/REJECT/EXECUTION) and a codec.

**Architecture (current):** Maven multi-module reactor.
- `finex-common` — domain model
- `finex-order-book` — `OrderBook`
- `finex-matching-engine` — `MatchingEngine`, `MatchResult`, `Trade`
- `finex-risk` — `RiskEngine`
- `finex-market-data` — `MarketDataPublisher`, `BookUpdate`, `TradeEvent`, `ExecutionEvent`
- `finex-api` — `OrderService` + `OrderController`
- (in progress) `finex-protocol` for binary codec

**Completed milestones:**
- Phases 1-7 committed or in progress (`ac84774` through Phase 7).

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs. ADR-000 through ADR-005.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn test` green after Phase 7 market-data integration.

**Next action:** Create `finex-protocol` module with binary message encoding/decoding.

**Important commands:**
```bash
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
