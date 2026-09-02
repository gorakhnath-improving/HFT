# PROGRESS LOG

Reverse-chronological. One entry per session/significant milestone.

---

## 2026-09-02 — Session 3: Phase 3 — Correct Order Book

**Phase:** 2 → 3 (in progress)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md` for Phase 3.
- Added `finex-order-book` Maven module, wired into parent `pom.xml`.
- Implemented `com.finex.orderbook.OrderBook`:
  - `TreeMap<BigDecimal, List<Order>>` for bids (descending price) and asks (ascending).
  - Per-price lists sorted by `sequence` for time priority.
  - `ConcurrentHashMap<Long, Order>` for fast id lookup on cancellation.
  - `addOrder`, `cancelOrder`, `bestBid`, `bestAsk`, `getBids`, `getAsks`.
  - Validation: only LIMIT orders, positive remaining quantity, resting statuses.
- Added `OrderBookTest` (10 tests) covering: empty book, best bid/ask, time priority at
  same price, cancellation, price level removal, rejection of non-LIMIT/wrong-symbol
  orders, repeated identical inputs determinism, and flat bid/ask views.
- ADR-003: TreeMap + per-price list baseline; measure alternatives before optimizing.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `OrderBookTest` 10/10, `InstrumentTest` 15/15,
  `HealthControllerTest` 1/1.

**Blockers:** None.

**Next session should:**
- Commit Phase 3 work.
- Start Phase 4 — Matching Engine: deterministic price-time-priority matching, full/partial
  fills, `Trade` generation, `Order` state updates. Matching engine likely becomes
  `finex-matching-engine` module or extends `finex-order-book`; decide as Phase 4 begins.

---

## 2026-09-02 — Session 2: Phase 2 — Financial Domain Model

**Phase:** 1 → 2 (in progress)

**Done:**
- Started Phase 2; updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md`.
- Added framework-agnostic domain model to `finex-common`:
  - Enums: `Side`, `OrderType`, `OrderStatus`, `InstrumentStatus`, `AccountStatus`,
    `DebitCredit`, `EntryType`, `LedgerAccountType`.
  - Records: `User`, `Account`, `Instrument`, `Order`, `Trade`, `Position`, `Balance`,
    `LedgerAccount`, `LedgerEntry`.
- Domain validation in constructors: positive prices/quantities/tick/lot sizes,
  non-negative balances/positions, blank string guards, non-null references.
- `Order` is immutable and provides `withFill(...)` / `cancelled(...)` copy methods to
  support fill/cancel state transitions without mutating the original.
- `Position.marketValue(...)` and `Position.totalPnl()` helper methods.
- `Balance.total()` helper.
- `InstrumentTest`: 15 unit tests covering construction, basic invariants, order fill
  transitions, trade creation, balance/position math, and ledger entry validation.
- ADR-002: `BigDecimal` for fixed-point money/quantity in the baseline (no custom
  wrappers yet; will measure before optimizing the hot path).
- Added AssertJ as a test dependency to `finex-common/pom.xml`.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `finex-common` 15/15 domain tests pass,
  `finex-api` `HealthControllerTest` still passes (1/1).

**Blockers:** None.

**Next session should:**
- Commit Phase 2 work.
- Start Phase 3 — Correct Order Book (TreeMap-backed, price-time priority) or continue
  expanding Phase 2 if additional entities (e.g. `ExecutionReport`) are needed.

---

## 2026-09-02 — Session 1: Project bootstrap

**Phase:** 0 → 1 (in progress)

**Done:**
- Read Master Plan (`../Master Plan.md`), extracted phased roadmap into `PROJECT_PLAN.md`.
- Decided: Maven multi-module build, Docker Compose for infra (Postgres/Prometheus/Grafana).
- `git init`, `.gitignore` created.
- Project memory files created: PROJECT_PLAN.md, PROGRESS.md (this file), TODO.md,
  AGENT_CONTEXT.md, DESIGN_DECISIONS.md.
- Environment verified: Java 25.0.2, Maven 3.9.16, Docker 29.4.2, Docker Compose v5.1.3.

- Scaffolded Maven parent POM (Java 25, Spring Boot 4.1.1 BOM) + `finex-common` (empty
  shared module) + `finex-api` (Spring Boot app) modules.
- `docker-compose.yml`: postgres (16-alpine), prometheus (v3.13.2), grafana (13.0.7),
  with Prometheus scrape config and a provisioned Grafana datasource under `docker/`.
- `finex-api`: `GET /api/v1/health` (checks DataSource connectivity), Flyway migration
  `V1__init.sql` (placeholder bootstrap marker table), `application.yml` reading DB
  connection from env vars with localhost defaults, actuator + prometheus endpoint exposed.
- Test: `HealthControllerTest` — Testcontainers-backed Postgres, full Spring context,
  asserts `/api/v1/health` returns 200 with `db: UP`. (Note: JUnit test classes must be
  named `*Test`/`Test*`, not `*IT`, for the default Surefire include pattern to pick them
  up — no Failsafe plugin configured yet.)
- `docs/` skeleton created (ARCHITECTURE.md, PROTOCOL.md, FINANCIAL_MODEL.md,
  performance/{BENCHMARKS,EXPERIMENTS,OPTIMIZATIONS}.md) — placeholders only.
- README.md with build/run/test instructions.
- ADR-000 (Maven) and ADR-001 (Docker infra, native app for now) recorded.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS (all modules).
- `mvn test` — SUCCESS, 1 test, real Testcontainers Postgres + Flyway + Spring context.
- `docker compose up -d postgres` (note: local machine's default 5432 was already bound by
  an unrelated container, used `DB_PORT=5442` override) + `mvn -pl finex-api
  spring-boot:run` + `curl localhost:8080/api/v1/health` → `{"status":"UP","db":"UP"}`.
- Stopped app process and `docker compose down` afterward; environment left clean.

**Blockers:** None. Note for future sessions: port 5432 may be occupied by unrelated local
Docker containers on this machine — pass `DB_PORT=<free-port>` to `docker compose up` if so.

**Next session should:**
- `git add -A && git commit` this bootstrap (not yet committed as of writing this entry).
- Start Phase 2 (Financial Domain Model): expand atomic tasks in TODO.md, then implement.
