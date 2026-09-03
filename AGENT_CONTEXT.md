# AGENT CONTEXT (keep short)

**Current phase:** Post-Phase-24 performance-engineering pass (evidence-driven optimization
cycle, OPT-003 completed). This is ongoing/iterative work, not a numbered master-plan phase.
**Current task:** OPT-003 (skip `markToMarket` when mark price is unchanged) completed,
measured, and documented. Next candidate: event-log / `BinaryCodec.encodePayload` cost
(not yet actioned).

**Architecture (current):** Maven multi-module reactor.
- `finex-common` — domain model
- `finex-order-book` — `OrderBook`
- `finex-matching-engine` — `MatchingEngine`, `MatchResult`, `Trade`
- `finex-risk` — `RiskEngine` (including per-account rate limiting)
- `finex-market-data` — market-data events (`hasSubscribers()` added in OPT-002)
- `finex-protocol` — binary codec
- `finex-event-log` — append-only events, replay
- `finex-shard` — symbol sharding
- `finex-ledger` — double-entry ledger
- `finex-portfolio` — positions and P&L (`lastMarkPrices` cache added in OPT-003)
- `finex-clearing` — trade clearing and fees
- `finex-settlement` — settlement orchestration
- `finex-load-generator` — configurable load generator
- `finex-benchmarks` — JMH/component/end-to-end benchmarks, JFR profiling,
  `SustainedSharedServiceDriver` (long-running, low-variance evidence driver)
- `finex-api` — `OrderService` + controllers + metrics + security filters

**Completed milestones:**
- Phases 1-24 implemented and committed (see PROJECT_PLAN.md for full history).
- OPT-001 (Phase 19): in-place resting-order updates in `OrderBook`/`MatchingEngine`.
- OPT-002 (this session): eliminated unconditional market-data snapshot construction
  (`BookUpdateFactory.aggregate`) when there are no subscribers. JFR-measured: was 56.8%
  of all sampled allocations and the #1 CPU hotspot; removed entirely. Measured
  throughput improvement ~+24% to +28% on a sustained 1.5M-order shared-`OrderService`
  workload.
- OPT-003 (this session): added a `lastMarkPrices` cache to `PortfolioService` so
  `markToMarket` skips a full all-accounts scan when the mark price has not changed.
  This was the top CPU frame after OPT-002 (`Position.mark`, 9% of CPU samples) and a
  major `BigDecimal` allocator. After the fix it does not appear in the top CPU or
  allocation samples. Throughput improved a further ~+39% (98,944 -> 137,563 ops/sec
  average on the sustained driver). Cumulative improvement vs baseline: ~+78%.
- Full `mvn test` green across all 16 modules after OPT-003.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs.

**Known problems:** On this machine, local port 5432 can be occupied by unrelated Docker
containers from other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.
The `exec-maven-plugin` `exec:java` goal in `finex-benchmarks` does not reliably honor
`-Dexec.mainClass` overrides on the CLI on this machine/plugin version (always runs the
pom-configured `BenchmarkRunner`); use `java -cp <classpath>` directly to run
`ProfileRunner` / `SustainedSharedServiceDriver` instead (see `OPTIMIZATION_EVIDENCE.md`
for the exact reproduction commands).

**Current benchmark:** See `docs/performance/FINAL_BENCHMARK_REPORT.md`, `BENCHMARKS.md`,
and `OPTIMIZATION_EVIDENCE.md` for the OPT-002 / OPT-003 before/after evidence.

**Last successful build:** `mvn test` green (all 16 modules) after OPT-003.

**Important commands:**
```bash
mvn -q -DskipTests install              # build + install all modules (needed before
                                         # running class files directly via `java -cp`)
mvn test                                # run full correctness suite

# JMH suite (short, higher-variance benchmarks)
mvn -q -pl finex-benchmarks dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
java -cp "finex-benchmarks/target/classes:$(cat /tmp/cp.txt)" com.finex.benchmarks.BenchmarkRunner

# Long, low-variance evidence driver (used for OPT-002 / OPT-003)
java -cp "finex-benchmarks/target/classes:$(cat /tmp/cp.txt)" \
  com.finex.benchmarks.SustainedSharedServiceDriver 1500000 500

# JFR profile of the above
java -XX:StartFlightRecording=filename=/tmp/finex.jfr,settings=profile \
  -cp "finex-benchmarks/target/classes:$(cat /tmp/cp.txt)" \
  com.finex.benchmarks.SustainedSharedServiceDriver 1500000 500
```
