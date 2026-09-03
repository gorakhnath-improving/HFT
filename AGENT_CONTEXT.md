# AGENT CONTEXT (keep short)

**Current phase:** Phase 24 — Final Benchmark Campaign completed; all 24 phases now done.
**Current task:** All requested project work is complete. `mvn test` is green.

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
- `finex-benchmarks` — JMH/component/end-to-end benchmarks, JFR profiling
- `finex-api` — `OrderService` + controllers + metrics + security filters

**Completed milestones:**
- Phases 1-22 implemented and committed.
- Phase 23: consolidated `docs/ARCHITECTURE.md`, `docs/PROTOCOL.md`, `docs/FINANCIAL_MODEL.md`,
  added ADR-010/011/012, updated `README.md`.
- Phase 24: added `MultiThreadedLoadGeneratorBenchmark`, produced
  `docs/performance/FINAL_BENCHMARK_REPORT.md` with honest 1M/sec assessment and roadmap.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.

**Current benchmark:** See `docs/performance/FINAL_BENCHMARK_REPORT.md` and `BENCHMARKS.md`.

**Last successful build:** `mvn test` green after Phase 24.

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
