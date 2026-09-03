# AGENT CONTEXT (keep short)

**Current phase:** Post-Phase-24 performance-engineering pass (evidence-driven optimization
cycle, OPT-007 completed). This is ongoing/iterative work, not a numbered master-plan phase.
**Current task:** OPT-007 (reduce event-log serialization allocation: `BinaryCodec.encodeToBytes`,
`CommandSerializer.toPayload`, `EventStore.append(Instant, String, byte[])` overload,
remove `Event` payload clone on the hot path) completed and tests pass. Next candidates:
OPT-008 (metrics batching), OPT-009 (randomized stress harness), OPT-010 (fixed-point
numerics), OPT-011 (lock-free order book).

**Architecture (current):** Maven multi-module reactor.
- `finex-common` — domain model
- `finex-order-book` — `OrderBook`
- `finex-matching-engine` — `MatchingEngine`, `MatchResult`, `Trade`
  (`MatchResult` now returns engine-local pre-sized `ArrayList`/`HashMap` directly)
- `finex-risk` — `RiskEngine` (including per-account rate limiting);
  `AccountRiskState` maintains O(1) running reservation totals (OPT-005)
- `finex-market-data` — market-data events (`hasSubscribers()` added in OPT-002)
- `finex-protocol` — binary codec (`BinaryCodec.encode` now reuses a per-thread
  `ByteArrayOutputStream`, OPT-006)
- `finex-event-log` — append-only events, replay
- `finex-shard` — symbol sharding
- `finex-ledger` — double-entry ledger
- `finex-portfolio` — positions and P&L (`lastMarkPrices` cache added in OPT-003)
- `finex-clearing` — trade clearing and fees
- `finex-settlement` — settlement orchestration
- `finex-load-generator` — configurable load generator
- `finex-benchmarks` — JMH/component/end-to-end benchmarks, JFR profiling,
  `SustainedSharedServiceDriver` (long-running, low-variance evidence driver; now
  reports per-order latency percentiles, OPT-004, and uses `BigDecimal` constants,
  OPT-006)
- `finex-api` — `OrderService` + controllers + metrics + security filters

**Completed milestones:**
- Phases 1-24 implemented and committed (see PROJECT_PLAN.md for full history).
- OPT-001 (Phase 19): in-place resting-order updates in `OrderBook`/`MatchingEngine`.
- OPT-002: eliminated unconditional market-data snapshot construction
  (`BookUpdateFactory.aggregate`) when there are no subscribers (~+24-28% shared
  `OrderService` throughput).
- OPT-003: added a `lastMarkPrices` cache to `PortfolioService` so `markToMarket`
  skips a full all-accounts scan when the mark price has not changed (~+39% further
  throughput).
- OPT-004: added per-order latency percentile measurement to
  `SustainedSharedServiceDriver`.
- OPT-005: maintained running `totalReservedCash` / `totalReservedPosition` in
  `AccountRiskState`, removing the O(open orders) stream/reduce on every
  `RiskEngine.validate` call (shared driver: 137.5k → 587.7k ops/s, **+327%**).
- OPT-006: removed `List.copyOf`/`Map.copyOf` in `MatchingEngine` (pre-sized
  collections), reused a `ThreadLocal<ByteArrayOutputStream>` in `BinaryCodec`, and
  pre-computed `BigDecimal` constants in the sustained driver (shared driver:
  587.7k → 671.1k ops/s, **+14.2%**; matching-engine JMH: 3.37M → 4.23M ops/s).
- OPT-007: added `BinaryCodec.encodeToBytes`, `CommandSerializer.toPayload`, and an
  `EventStore.append(Instant, String, byte[])` overload; removed the `Event` payload
  clone from the hot path; `mvn test` green across all 16 modules.
- Full `mvn test` green across all 16 modules after OPT-006 and OPT-007.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs.

**Known problems:**
- On this machine, local port 5432 can be occupied by unrelated Docker containers from
  other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.
- The `exec-maven-plugin` `exec:java` goal in `finex-benchmarks` does not reliably honor
  `-Dexec.mainClass` overrides on this machine/plugin version (always runs the
  pom-configured `BenchmarkRunner`); use `java -cp <classpath>` directly to run
  `ProfileRunner` / `SustainedSharedServiceDriver` instead (see `OPTIMIZATION_EVIDENCE.md`
  for the exact reproduction commands).
- The sustained-driver benchmark environment became unstable during this session (memory
  pressure / compressor activity, unrelated Docker/container churn). Measured throughput
  dropped to ~200–300k ops/sec for both the committed OPT-006 baseline and the new
  OPT-007 code, so reliable before/after numbers for OPT-007 are not available. Re-run
  on a quiet, dedicated environment before declaring the OPT-007 speed-up.

**Remaining backlog:** OPT-008 (metrics batching/offloading), OPT-009 (randomized
financial-invariant stress harness), OPT-010 (fixed-point numerics in hot path),
OPT-011 (lock-free / single-writer order book per symbol shard).

**Current benchmark:** See `docs/performance/FINAL_BENCHMARK_REPORT.md`, `BENCHMARKS.md`,
`OPTIMIZATIONS.md`, `OPTIMIZATION_EVIDENCE.md`, and `OPTIMIZATION_PLAN.md`.

**Last successful build:** `mvn test` green (all 16 modules) after OPT-006.

**Important commands:**
```bash
mvn -q -DskipTests install              # build + install all modules (needed before
                                         # running class files directly via `java -cp`)
mvn test                                # run full correctness suite

# JMH suite (short, higher-variance benchmarks)
mvn -q -pl finex-benchmarks dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
java -cp "finex-benchmarks/target/classes:$(cat /tmp/cp.txt)" com.finex.benchmarks.BenchmarkRunner

# Long, low-variance evidence driver (used for OPT-002 through OPT-006)
java -cp "finex-benchmarks/target/classes:$(cat /tmp/cp.txt)" \
  com.finex.benchmarks.SustainedSharedServiceDriver 1500000 500

# JFR profile of the above
java -XX:StartFlightRecording=filename=/tmp/finex.jfr,settings=profile \
  -cp "finex-benchmarks/target/classes:$(cat /tmp/cp.txt)" \
  com.finex.benchmarks.SustainedSharedServiceDriver 1500000 500
```
