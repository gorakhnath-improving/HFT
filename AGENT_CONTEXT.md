# AGENT CONTEXT (keep short)

**Current phase:** Phase 4 — Matching Engine (done; committing)
**Current task:** Commit Phase 4; next is Phase 5 — REST/API Layer

**Architecture (current):** Maven multi-module reactor. `finex-common` has the domain
model. `finex-order-book` has the `OrderBook`. `finex-matching-engine` owns a per-symbol
`MatchingEngine` that consumes the order book and produces `Trade`s. `finex-api` is the
Spring Boot admin app. Dependency chain: `finex-common` ← `finex-order-book` ←
`finex-matching-engine` ← `finex-api` (planned).

**Completed milestones:**
- Phase 1 committed (`ac84774`).
- Phase 2 committed (`f975cb9`).
- Phase 3 committed (`32a22ca`).
- Phase 4 `MatchingEngine` implemented and tested (15/15 `MatchingEngineTest`).

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs. ADR-000: Maven; ADR-001:
Docker Compose for infra, native Maven app; ADR-002: `BigDecimal` for fixed-point
money/quantity; ADR-003: TreeMap + per-price list order book baseline; ADR-004:
single-symbol, single-threaded matching engine baseline.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn -q -DskipTests package` and `mvn test` green (Phase 4,
`MatchingEngineTest` 15/15 + `OrderBookTest` 10/10 + `InstrumentTest` 15/15 +
`HealthControllerTest` 1/1).

**Next action:** `git add -A && git commit` Phase 4, then start Phase 5: REST/API layer
(`finex-api`) with endpoints for order submit/cancel/query backed by a `MatchingEngine` or
a small `OrderService` orchestrator. Need to add `finex-matching-engine` as a dependency
of `finex-api`.

**Important commands:**
```bash
# From finex/ directory:
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
