# TODO — Active Task Queue

Only the current phase's atomic tasks live here in detail. See PROJECT_PLAN.md for the
full roadmap.

## Phase 1 — Repository Bootstrap (current)

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
- [ ] Commit bootstrap work (next action)

## Next Phase (not started)

Phase 2 — Financial Domain Model. Do not expand tasks until Phase 1 is committed.
When starting Phase 2, break down: User/Account/Instrument/Order/Trade/Position/Balance/
LedgerAccount/LedgerEntry as plain domain classes in `finex-common`, plus unit tests for
each, before wiring any persistence.
