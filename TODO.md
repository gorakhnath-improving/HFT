# TODO — Active Task Queue

Only the current phase's atomic tasks live here in detail. See PROJECT_PLAN.md for the
full roadmap.

## Phase 1 — Repository Bootstrap (done)

- [x] Init git repo, .gitignore
- [x] Create project memory files
- [x] Create parent `pom.xml` (Java 25, Spring Boot 4.1.1 BOM, module list)
- [x] Create `finex-common` module
- [x] Create `finex-api` module (health endpoint, Flyway, Postgres)
- [x] `docker-compose.yml` at repo root (postgres, prometheus, grafana)
- [x] README, docs skeleton
- [x] Verify build + tests
- [x] Commit (`ac84774`)

## Phase 2 — Financial Domain Model (done)

- [x] ADR-002: `BigDecimal` for money/quantity
- [x] Domain enums and records in `finex-common`
- [x] 15 unit tests (`InstrumentTest`)
- [x] Commit (`f975cb9`)

## Phase 3 — Correct Order Book (done)

- [x] Add `finex-order-book` to parent `pom.xml`
- [x] Create `finex-order-book` module
- [x] `OrderBook` class (TreeMap, price-time priority)
- [x] 10 unit tests (`OrderBookTest`)
- [x] ADR-003: order book data structure
- [ ] Commit Phase 3 work

## Phase 4 — Matching Engine (next)

Goal: Deterministic price-time-priority matching; full/partial fills; `Trade` generation;
`Order` state updates. Dependencies: Phase 3 (done).
