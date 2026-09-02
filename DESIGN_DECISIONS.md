# Design Decisions (ADRs)

Format: Context / Options / Decision / Reason / Tradeoffs / Consequences.

---

## ADR-000: Build tool — Maven

**Context:** Master Plan allows Maven or Gradle for the multi-module Java/Spring Boot
project.

**Options:**
- Gradle (Kotlin DSL): faster incremental builds, common in perf-sensitive Java projects.
- Maven: ubiquitous in enterprise Spring Boot, verbose XML, stable multi-module conventions.

**Decision:** Maven.

**Reason:** Explicit user preference; also plays well with Spring Boot's default tooling
and most engineers' familiarity, reducing onboarding friction for a portfolio project
reviewers will clone and build.

**Tradeoffs:** Slower incremental builds than Gradle; more verbose module wiring.

**Consequences:** Multi-module reactor with a parent `pom.xml` managing dependency/plugin
versions (Spring Boot BOM, Testcontainers BOM, etc.). New modules added as new
`<module>` entries as phases progress.

---

## ADR-001: Infrastructure via Docker Compose, app runs natively (for now)

**Context:** Master Plan requires Docker/Docker Compose for infrastructure, and the app
needs Postgres (later Prometheus/Grafana, possibly Kafka-like queues) during development.

**Options:**
- Containerize everything (app + infra) via Docker Compose from day one.
- Run only infra components (Postgres, Prometheus, Grafana) in Docker; run the Spring
  Boot app natively via Maven during development for fast iteration/debugging.

**Decision:** Infra in Docker Compose; app runs natively via `mvn spring-boot:run` during
development. A Dockerfile for the app itself will be added later (Phase 20+/23) once the
service boundaries stabilize, so CI/deployment can containerize it too.

**Reason:** Fast local iteration (no image rebuild per code change) while still getting
reproducible, disposable infra dependencies as requested.

**Tradeoffs:** Local dev environment isn't 100% identical to a fully-containerized
deployment until the app Dockerfile is added.

**Consequences:** `docker-compose.yml` at repo root defines `postgres`, `prometheus`,
`grafana`. `finex-api/src/main/resources/application.yml` reads DB connection info from
environment variables with sensible localhost defaults.

---

## ADR-002: Money / fixed-point numeric representation — `java.math.BigDecimal`

**Context:** FinEx handles prices, quantities, P&L, and ledger amounts. Master Plan §17
prohibits blind use of floating-point arithmetic for money. Phase 2 requires a
representation for the domain model that is exact, deterministic, and reviewable.

**Options:**
- `double` / `float`: fast, but inexact and unsuitable for financial calculations
  (violates Master Plan §17).
- `long` with an implicit fixed scale (e.g. cents): fast and exact, but leaks the scale
  convention into every calculation and is error-prone.
- Custom `Money` / `Quantity` wrapper classes: explicit, but adds API overhead and
  requires defining arithmetic methods before we have any benchmark evidence they are
  needed on the hot path.
- `java.math.BigDecimal`: standard, arbitrary-precision decimal, exact, deterministic,
  understood by financial developers, and works well for the baseline domain model.

**Decision:** Use `BigDecimal` directly in the baseline domain model. No custom money
wrapper yet. Precision/scale validation is done at object construction; calculations are
carried out with `BigDecimal.ONE` / `add` / `subtract` / `multiply` and exact arithmetic
where possible. If later profiling shows allocation or arithmetic cost on the HFT hot
path (matching engine), we may introduce `long` fixed-point or a specialized immutable
wrapper and record that in ADR-00X.

**Reason:** Phase 2 is correctness-first. `BigDecimal` gives exact decimal arithmetic
without scale leakage, satisfies the Master Plan requirement, and is easy for reviewers
to understand. Prematurely adding a custom wrapper before we have a hot path to optimize
would be over-engineering.

**Tradeoffs:** `BigDecimal` creates more objects than primitive `long`s and has higher
arithmetic cost than fixed-point. It is not ideal for the eventual 1M orders/sec hot
path, but that is not the current concern (Master Plan §50: "Correctness → Tests →
Observability → Benchmark → Profile → Optimize").

**Consequences:**
- All price, quantity, P&L, and balance fields in `finex-common` are `BigDecimal`.
- Constructors validate positivity / non-negativity of numeric values.
- No `double`/`float` is used for money anywhere in the codebase; this is enforced by
  code review and later by static analysis if needed.
- Future optimization will be measured before it is applied (JMH/component benchmarks).
