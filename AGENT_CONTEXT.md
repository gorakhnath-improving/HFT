# AGENT CONTEXT (keep short)

**Current phase:** Phase 2 — Financial Domain Model (done; committing)
**Current task:** Commit Phase 2; next is Phase 3 — Correct Order Book

**Architecture (current):** Maven multi-module reactor. `finex-common` now contains the
framework-free FinEx domain model (enums + records for User, Account, Instrument, Order,
Trade, Position, Balance, LedgerAccount, LedgerEntry). `finex-api` is the Spring Boot
administrative app from Phase 1.

**Completed milestones:**
- Phase 1 completed and committed (`ac84774`).
- Phase 2 domain model implemented and tested (15/15 `InstrumentTest` assertions pass,
  `mvn test` green).
- ADR-002: `BigDecimal` for money/quantity baseline.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs. ADR-000: Maven; ADR-001:
Docker Compose for infra, native Maven app; ADR-002: `BigDecimal` for fixed-point
money/quantity in baseline (measure before optimizing hot path).

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.
`com.finex.common.Placeholder` from Phase 1 skeleton is still in the repo and can be
removed once confirmed safe.

**Current benchmark:** N/A.

**Last successful build:** `mvn -q -DskipTests package` and `mvn test` green (Phase 2).

**Next action:** `git add -A && git commit` Phase 2, then start Phase 3: a simple
TreeMap-backed order book with deterministic price-time priority (BUY high price first,
SELL low price first; earlier order wins at same price).

**Important commands:**
```bash
# From finex/ directory:
mvn -q -DskipTests package             # build all modules
mvn test                               # run tests
```
