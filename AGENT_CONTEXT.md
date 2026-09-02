# AGENT CONTEXT (keep short)

**Current phase:** Phase 1 — Repository Bootstrap (functionally complete, pending commit)
**Current task:** Commit bootstrap; then start Phase 2 (Financial Domain Model)

**Architecture (current):** Maven multi-module reactor at `finex/`. Modules:
`finex-common` (empty shared module, placeholder class only), `finex-api` (Spring Boot 4.1.1
app on Java 25, exposes `GET /api/v1/health` + actuator/prometheus endpoints, Flyway-managed
Postgres connection). More modules (`finex-order-book`, `finex-matching-engine`,
`finex-risk`, etc.) added as their phases start — see PROJECT_PLAN.md / Master Plan §40.

**Completed milestones:**
- Project memory files created (this file, PROJECT_PLAN.md, PROGRESS.md, TODO.md,
  DESIGN_DECISIONS.md).
- Maven parent + `finex-common` + `finex-api` scaffolded and building.
- `docker-compose.yml` (postgres, prometheus, grafana) + provisioning config under `docker/`.
- `finex-api` health endpoint verified end-to-end against real Dockerized Postgres.

**Important decisions:** See DESIGN_DECISIONS.md / ADRs. ADR-000: Maven over Gradle
(user preference). ADR-001: infra via Docker Compose (Postgres/Prometheus/Grafana); app
runs natively via Maven for now, not containerized itself yet. Stack picks: Java 25,
Spring Boot 4.1.1 (current supported line — 3.5.x reached OSS EOL), Testcontainers 2.0.5
(artifact renamed to `testcontainers-postgresql` in 2.x).

**Known problems:** JUnit test classes must be named `*Test`/`Test*` (not `*IT`) since no
Failsafe plugin is configured — default Surefire include pattern won't pick up `*IT.java`.
On this dev machine, local port 5432 can be occupied by unrelated Docker containers from
other projects; pass `DB_PORT=<free-port>` to `docker compose up` / the app if so.

**Current benchmark:** N/A — no matching engine yet.

**Last successful build:** `mvn -q -DskipTests package` and `mvn test` both green (1 test,
Testcontainers Postgres). End-to-end docker-compose + spring-boot:run + curl health verified.

**Next action:** `git add -A && git commit` the bootstrap, then expand Phase 2 atomic tasks
in TODO.md (domain model classes in finex-common) and start implementing.

**Important commands:**
```bash
# From finex/ directory:
docker compose up -d postgres          # start Postgres
mvn -q -DskipTests package             # build all modules
mvn -pl finex-api spring-boot:run      # run the API app
curl localhost:8080/api/v1/health      # check health
mvn test                               # run tests
docker compose down                    # stop infra
```
