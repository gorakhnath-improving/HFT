# AGENT CONTEXT (keep short)

**Current phase:** Post-Phase-24 performance-engineering pass (evidence-driven optimization
cycle). This is ongoing/iterative work, not a numbered master-plan phase.
**Current task:** OPT-007's code is done and tests pass, but its performance claim was
re-validated in a controlled A/B (git worktree, OPT-006 `819cd63` vs OPT-007 `635219f`,
same JDK/JVM/workload, 5 interleaved reps) and reclassified as **NO MEASURABLE
IMPROVEMENT** — the mean delta was smaller than run-to-run noise on both commits. Fresh
JFR profiling on HEAD shows `BigDecimal.valueOf`/boxing (not metrics) as the dominant
allocation source, spread across risk/matching/settlement. Per the plan's own
evidence-driven reordering rule, the recommended next step is **OPT-009** (randomized
differential/financial-invariant stress harness) before attempting **OPT-010**
(fixed-point numerics), since OPT-010 is high-risk without that safety net. OPT-008
(metrics batching) is deprioritized — it does not appear in the current top frames.

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
  clone from the hot path; `mvn test` green across all 16 modules. Controlled A/B
  (5 reps, git worktree) found NO MEASURABLE IMPROVEMENT vs OPT-006 — kept for the
  allocation-reduction engineering benefit only, not cited as a speedup.
- Full `mvn test` green across all 16 modules after OPT-006 and OPT-007.
- A settlement account-key `ConcurrentHashMap<Long, String>` cache was prototyped to
  attack `SettlementService`/`InMemoryLedger` allocation, then reverted (not committed):
  `long` → `Long` boxing on every cache lookup traded one allocation for another.

**Important decisions:** See `DESIGN_DECISIONS.md` / ADRs.

**Known problems:**
- On this machine, local port 5432 can be occupied by unrelated Docker containers from
  other projects; pass `DB_PORT=<free-port>` when starting `docker compose up`.
- The `exec-maven-plugin` `exec:java` goal in `finex-benchmarks` does not reliably honor
  `-Dexec.mainClass` overrides on this machine/plugin version (always runs the
  pom-configured `BenchmarkRunner`); use `java -cp <classpath>` directly to run
  `ProfileRunner` / `SustainedSharedServiceDriver` instead (see `OPTIMIZATION_EVIDENCE.md`
  for the exact reproduction commands).
- The sustained-driver benchmark environment was unstable in the OPT-007 implementation
  session (memory pressure / compressor activity, unrelated Docker/container churn),
  producing ~200–300k ops/sec for both OPT-006 and OPT-007. A follow-up controlled A/B
  (git worktree, both commits, same JVM/workload, 5 interleaved reps) on a calmer machine
  state reproduced numbers consistent with the OPT-006 baseline (~690-701k ops/sec) for
  both commits, confirming the earlier low readings were environmental, not a
  regression — but also showing OPT-007 itself has no measurable throughput effect.
  This machine is shared with an interactive Devin session, Microsoft Defender, and a
  browser; treat single-run numbers on it with caution and prefer multi-rep A/Bs.

**Remaining backlog (reordered based on fresh profiling — see `OPTIMIZATION_PLAN.md`):**
OPT-009 (randomized financial-invariant stress harness) is next, ahead of OPT-010
(fixed-point numerics, gated behind OPT-009) and OPT-008 (metrics batching, deprioritized
since `MetricsService` does not appear in current top CPU/allocation frames). OPT-011
(lock-free / single-writer sharded order book) remains last.

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
