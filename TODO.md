# TODO — Active Task Queue

Only the current phase's atomic tasks live here in detail. See PROJECT_PLAN.md for the
full roadmap.

## Phase 1 — Repository Bootstrap (done)

- [x] Init git repo, .gitignore
- [x] Create project memory files
- [x] Create parent `pom.xml` (Java 25, Spring Boot 4.1.1 BOM, module list)
- [x] Create `finex-common` module (empty, just group/artifact wiring for now)
- [x] Create `finex-api` module (web, actuator, jdbc, flyway, postgresql driver;
      `application.yml`; `V1__init.sql`; `HealthController`; Testcontainers test)
- [x] `docker-compose.yml` at repo root (postgres, prometheus, grafana)
- [x] `README.md`: prerequisites, build, docker compose up, run, test, curl health
- [x] `docs/` skeleton files (empty headers, filled in later phases)
- [x] Verify: `mvn -q -DskipTests package` succeeds
- [x] Verify: `mvn test` succeeds (real Testcontainers Postgres)
- [x] Verify: `docker compose up -d postgres` + app boot + `curl` health check works
- [x] Commit bootstrap work (`ac84774`)

## Phase 2 — Financial Domain Model (done)

- [x] ADR-002: Money / fixed-point numeric representation (BigDecimal)
- [x] Create `com.finex.common.domain.enums` package:
  - [x] `Side` (BUY, SELL)
  - [x] `OrderType` (LIMIT, MARKET)
  - [x] `OrderStatus` (NEW, OPEN, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED)
  - [x] `InstrumentStatus` (ACTIVE, HALTED)
  - [x] `AccountStatus` (ACTIVE, FROZEN, CLOSED)
  - [x] `DebitCredit` (DEBIT, CREDIT)
  - [x] `EntryType` (TRADE, FEE, SETTLEMENT, ADJUSTMENT)
  - [x] `LedgerAccountType` (CASH, ASSET, FEE, REALIZED_PNL)
- [x] Create `com.finex.common.domain` package — records/classes:
  - [x] `User`, `Account`, `Instrument`
  - [x] `Order`, `Trade`
  - [x] `Position`, `Balance`, `LedgerAccount`, `LedgerEntry`
- [x] Add unit tests for construction and basic invariants (`InstrumentTest`)
- [x] Build + `mvn test` green
- [x] Update `PROGRESS.md` / `AGENT_CONTEXT.md`
- [ ] Commit Phase 2 work

## Phase 3 — Correct Order Book (next)

Goal: Simple, correct order book (e.g. TreeMap-backed) with deterministic price-time
priority. Dependencies: Phase 2 (done).
