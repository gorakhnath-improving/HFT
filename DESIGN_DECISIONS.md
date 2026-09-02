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
