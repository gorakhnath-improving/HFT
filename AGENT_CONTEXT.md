# AGENT CONTEXT (keep short)

**Current phase:** Phase 5 — REST/API Layer (done; committing)
**Current task:** Commit Phase 5; next is Phase 6 — Risk Engine or whichever phase you choose

**Architecture (current):** Maven multi-module reactor. `finex-common` has the domain model;
`finex-order-book` has `OrderBook`; `finex-matching-engine` has `MatchingEngine`;
`finex-api` exposes `OrderController` over a per-symbol `OrderService`. Dependency chain:
`finex-common` ← `finex-order-book` ← `finex-matching-engine` ← `finex-api`.

**Completed milestones:**
- Phases 1-5 committed (`ac84774`, `f975cb9`, `32a22ca`, `081d4ab`, next commit).

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs. ADR-000: Maven; ADR-001:
Docker Compose for infra, native Maven app; ADR-002: `BigDecimal` for fixed-point
money/quantity; ADR-003: TreeMap + per-price list order book baseline; ADR-004:
single-symbol, single-threaded matching engine baseline.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn -q -DskipTests package` and `mvn test` green (Phase 5,
`OrderControllerTest` 7/7 + `MatchingEngineTest` 15/15 + `OrderBookTest` 10/10 +
`InstrumentTest` 15/15 + `HealthControllerTest` 1/1).

**Next action:** `git add -A && git commit` Phase 5, then choose Phase 6 (Risk Engine),
Phase 7 (Market Data), Phase 8 (Binary Protocol), or another priority.

**Important commands:**
```bash
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
mvn -pl finex-api spring-boot:run      # run the API app (needs Postgres)
```
