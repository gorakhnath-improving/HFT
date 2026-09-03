# AGENT CONTEXT (keep short)

**Current phase:** Phase 22 — Security / API keys / account isolation (batched: Phases 17-22
requested; all now completed)
**Current task:** All requested phases complete; full test suite green.

**Architecture (current):** Maven multi-module reactor.
- `finex-common` — domain model
- `finex-order-book` — `OrderBook`
- `finex-matching-engine` — `MatchingEngine`, `MatchResult`, `Trade`
- `finex-risk` — `RiskEngine` (including per-account rate limiting)
- `finex-market-data` — market-data events
- `finex-protocol` — binary codec
- `finex-event-log` — append-only events, replay
- `finex-shard` — symbol sharding
- `finex-ledger` — double-entry ledger
- `finex-portfolio` — positions and P&L
- `finex-clearing` — trade clearing and fees
- `finex-settlement` — settlement orchestration
- `finex-load-generator` — configurable load generator
- `finex-api` — `OrderService` + controllers + metrics + security filters
- `finex-benchmarks` — JMH/component/end-to-end benchmarks and JFR profiling harness

**Completed milestones:**
- Phases 1-16 committed.
- Phase 17: JMH benchmarks with baseline numbers in `docs/performance/BENCHMARKS.md`.
- Phase 18: `ProfileRunner` JFR harness and `docs/performance/PROFILING.md`.
- Phase 19: In-place `OrderBook.replaceOrder` optimization recorded in `OPTIMIZATIONS.md`.
- Phase 20: Micrometer metrics, Prometheus endpoint, Grafana dashboard.
- Phase 21: Failure/chaos tests for invalid input and corrupt events.
- Phase 22: API-key authentication and account isolation.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** See `docs/performance/BENCHMARKS.md`.

**Last successful build:** `mvn test` green after Phase 22.

**Important commands:**
```bash
mvn -q -DskipTests package              # build all modules
mvn test                                # run tests
mvn -pl finex-benchmarks exec:java \
  -Dexec.mainClass=com.finex.benchmarks.BenchmarkRunner
mvn -pl finex-benchmarks exec:java \
  -Dexec.mainClass=com.finex.benchmarks.ProfileRunner \
  -Dexec.args="/tmp/finex-profile.jfr"
```
