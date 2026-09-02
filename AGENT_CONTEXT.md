# AGENT CONTEXT (keep short)

**Current phase:** Phase 3 — Correct Order Book (done; committing)
**Current task:** Commit Phase 3; next is Phase 4 — Matching Engine

**Architecture (current):** Maven multi-module reactor. `finex-common` has the domain
model. `finex-order-book` has a TreeMap-based `OrderBook` with deterministic price-time
priority. `finex-api` is the Spring Boot admin app.

**Completed milestones:**
- Phase 1 committed (`ac84774`).
- Phase 2 committed (`f975cb9`) with domain model and ADR-002.
- Phase 3 `OrderBook` implemented and tested (10/10 tests pass) with ADR-003.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs. ADR-000: Maven; ADR-001:
Docker Compose for infra, native Maven app; ADR-002: `BigDecimal` for fixed-point
money/quantity; ADR-003: TreeMap + per-price list order book baseline.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** N/A.

**Last successful build:** `mvn -q -DskipTests package` and `mvn test` green (Phase 3,
`OrderBookTest` 10/10 + `InstrumentTest` 15/15 + `HealthControllerTest` 1/1).

**Next action:** `git add -A && git commit` Phase 3, then start Phase 4: Matching Engine
(price-time-priority matching, full/partial fills, `Trade` generation, `Order` state
updates). Consider whether to add a `finex-matching-engine` module or keep matching
logic alongside `OrderBook`.

**Important commands:**
```bash
# From finex/ directory:
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
