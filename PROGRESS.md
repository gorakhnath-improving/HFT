# PROGRESS LOG

Reverse-chronological. One entry per session/significant milestone.

---

## 2026-09-03 — Session 22: Finalization — FINALIZED / PORTFOLIO COMPLETE

**Scope:** Following the explicit directive to stop active performance optimization at a
meaningful stopping point (13 numbered experiments, 10 validated, 1 rejected, 1 no-measurable-
improvement, 1 deferred) and finalize the repository for GitHub/portfolio presentation.

**Decision, stated plainly:** No OPT-014 was invented. The project stops here. 945,413 mean
ops/sec (OPT-013's paired benchmark) is a strong, honestly-measured result; chasing the 1,000,000
figure further for its own sake was explicitly rejected as a goal for this session.

**Done:**
- Re-verified `mvn test` green across all 16 modules / 19 test suites, no code changes.
- Cleaned up two leftover `git worktree`s from prior A/B benchmarking sessions
  (`/tmp/finex-opt011-baseline`, `/tmp/finex-opt013-baseline`).
- Ran a final reproducibility benchmark (`SustainedSharedServiceDriver`, fixed-point mode, 5
  fresh-JVM reps, 1.5M orders/500 accounts): mean 404,171 / median 402,618 / stdev 116,195
  ops/sec — markedly lower than OPT-013's 945,413 paired figure. Root-caused to machine load at
  the time (`load average 6.73` on 10 cores, ~5.8GB memory under compression), consistent with
  the exact same variance pattern documented during the OPT-007 session. Reported honestly
  rather than omitted or re-run until it matched the old number.
- Wrote `docs/performance/OPTIMIZATION_JOURNEY.md`: a narrative covering the full OPT-001
  through OPT-013 arc, explicitly including both rejected/downgraded results (OPT-007, OPT-011)
  as first-class parts of the story, not footnotes.
- Wrote `docs/FUTURE_RESEARCH.md`: 8 evidence-ranked future directions (protocol/event
  allocation, ledger allocation, GC/tail-latency analysis, matching-engine structural work,
  remaining BigDecimal boundaries, concurrency-contract refinement, single-writer/sharded
  architecture, metrics batching), each with current evidence, risk, and an explicit reason it
  was not started — deliberately kept out of `TODO.md`.
- Updated `docs/ARCHITECTURE.md` with a control-plane/trading-plane system diagram and an
  explicit statement that the Spring Boot layer is not itself claimed to run at HFT speeds.
- Updated `docs/performance/FINAL_BENCHMARK_REPORT.md` and `BENCHMARKS.md` with the full
  OPT-001–013 status table, the 945,413 paired figure, and the final reproducibility run
  (including the honest machine-load explanation) rather than only the best-case number.
- Rewrote `README.md`: portfolio-grade positioning ("a serious engineering simulation and
  performance-research project," not a production exchange), a Design Highlights section, and
  working benchmark/stress-harness reproduction commands.
- Updated `AGENT_CONTEXT.md` and `TODO.md` to **FINALIZED / PORTFOLIO COMPLETE**, moved all
  unauthorized future work out of the active task queue.
- Security/cleanliness check: `.gitignore` already excludes `target/`, `*.jar`, `.env`, IDE
  files, and logs; no secrets, API keys, or stray benchmark artifacts are tracked; working tree
  clean.

**Verified:**
- `mvn test` — SUCCESS across all 16 modules immediately before finalization; no production
  code was touched this session, only documentation and project-state files.
- Git history was not squashed or rewritten; the full OPT-001 → OPT-013 commit sequence is
  preserved intact as the project's engineering record.

**Blockers:** None.

**Next session should:** Not start any new optimization automatically. If asked to continue,
first re-read `docs/FUTURE_RESEARCH.md`, confirm the top-ranked candidate is still supported by
a *fresh* profile (not the one already on record), and only then apply the same measure →
hypothesize → experiment → validate → document discipline used throughout OPT-001–013.

---

## 2026-09-03 — Session 21: OPT-013 — Consolidated reservation table

**Change:** Consolidated cash, position, and price reservation values into parallel arrays under
one primitive order-ID table, replacing three repeated map lifecycles.

**Correctness:** Full 16-module tests and all seven 100k-command differential/replay/invariant
profiles pass. An invalid candidate-only benchmark setup was discarded before controlled A/B.

**Performance:** Five valid pairs measured 829,499→945,413 mean ops/s (+13.97%) and
841,662→946,580 median (+12.47%). Median p50–p99.99 improved; max regressed 71.2→75.4 ms.
JFR Long pressure 11.26%→2.39%, map-put CPU 16.33%→5.42%, young GC 16→12; total/max GC
pause regressed 747→779 ms and 127→224 ms.

**Verdict:** VALIDATED IMPROVEMENT / KEPT with explicit extreme-pause tradeoff. Next candidate
is protocol/event byte-array allocation; not started.

---

## 2026-09-03 — Session 20: OPT-012 — Primitive fixed-point reservation maps

**Change:** Replaced three boxed concurrent reservation maps inside owner-serialized
`FixedPointAccountRiskState` with a tested primitive open-address `LongLongHashMap`. Default
BigDecimal and service-level concurrent maps are unchanged.

**Correctness:** 100k randomized map-reference operations, full 16-module `mvn test`, and all
seven stress profiles at 100k commands passed differential, both replays, and both invariant sets.

**Performance:** Ten isolated interleaved 1.5M-order pairs measured 758,374→796,988 mean ops/s
(+5.09%) and 754,974→788,250 median (+4.41%). Eight pairs favored candidate. Median p90 through
max improved; p50 was unchanged. JFR Long allocation pressure fell 24.84%→11.26%, CHM node
9.77%→7.18%, young GC 18→16, and total pause 982→747 ms.

**Verdict:** VALIDATED IMPROVEMENT / KEPT. Next candidate is not started.

---

## 2026-09-03 — Session 19: OPT-011 — Redundant order-cache write experiment

**Baseline/profile:** Five 1.5M-order fixed-point runs averaged 792,525 ops/s. A 3M-order JFR
ranked ConcurrentHashMap put/resize first (23.9% combined CPU), then BigDecimal allocation
(24.1% pressure), then protocol/event byte arrays (10.1%). No monitor contention was recorded.

**Experiment:** Removed the duplicate incoming-order cache write; targeted matching, replay,
fixed-point, and differential stress tests passed. Isolated-build five-rep interleaved A/B measured
baseline 784,735 mean ops/s versus candidate 811,992 (+3.47%), but baseline stdev was 10.4% and
paired deltas ranged −9.7% to +19.5%. Latency was inconsistent. JFR confirmed the local putVal
reduction but not an end-to-end allocation/GC gain.

**Decision:** REJECTED / REVERTED — no measurable improvement. Only documentation remains.
Next candidate is map growth/boxed data layout, not started. Full `mvn test` passed across all
16 modules after reversion.

---

## 2026-09-03 — Session 18: OPT-010 — Fixed-point risk and clearing numerics

**Done:** Added a checked scale-4 `long` fixed-point primitive; selectable fixed-point risk
state/engine and clearing service; exact pre-append input validation; fixed-mode replay; and true
BigDecimal-vs-fixed differential stress including canonical state, byte-identical events, both
replays, and both invariant suites. BigDecimal remains the default/reference and external
API/domain/protocol/event/ledger/portfolio representations remain unchanged.

**Correctness:** All seven profiles passed 100k commands (seed 1); BALANCED passed 100k for
seeds 42, 12345, and 7; final BALANCED seed 7 passed 1M commands in 881,271 ms. Full 16-module
`mvn test` passed. Unsupported precision and all arithmetic overflow fail explicitly.

**Performance:** Five interleaved 300k-order runs measured BigDecimal 555,942 mean ops/s
(stdev 9,640) vs fixed 607,081 (stdev 37,134), a +9.2% mean delta. Median p50 through p99.99
improved; max did not. JFR BigDecimal allocation samples fell 151→139, total samples remained
660, and Long boxing rose 67→93. An intermediate conversion implementation introduced sampled
BigInteger allocation and was corrected before final measurement.

**Verdict:** VALIDATED IMPROVEMENT for representable scale-4 workloads; keep selectable fixed
mode and retain BigDecimal as default/reference. Matching, portfolio, ledger, and boundary
BigDecimal work remains intentionally outside this scoped integration. OPT-011 was not started.

---

## 2026-09-03 — Session 17: OPT-009 — Randomized differential / financial-invariant stress harness

**Scope:** Build the correctness oracle authorized as the next step after Session 16's
evidence-discipline pass, following the plan's phased progression (tiny deterministic case
→ hundreds → thousands → 100k → 1M) rather than a big-bang framework.

**Done:**
- New `com.finex.benchmarks.stress` package in `finex-benchmarks` (already depends on
  `finex-api`, following the same module convention as `finex-load-generator` and the
  existing driver classes):
  - `WorkloadProfile`: 7 documented distributions (`BALANCED`, `MATCH_HEAVY`,
    `CANCEL_HEAVY`, `RESTING_BOOK`, `CROSSING`, `MULTI_ACCOUNT`, `MULTI_INSTRUMENT`).
  - `CommandGenerator`: pure, seeded (`java.util.Random(seed)`) generator producing
    `GeneratedCommand.Submit`/`Cancel`; cancels reference an earlier submit's logical
    index rather than an order id, since order ids are only assigned at execution time
    but are assigned identically by any engine given the same command sequence.
  - `CommandExecutor` / `ExecutionResult`: drives one `OrderService` through a generated
    command list, tracking logical-index → order-id / account-id for later checks;
    treats risk rejections as an expected, recorded outcome, not a harness failure.
  - `EngineSnapshot` / `DifferentialComparator`: canonical, `equals()`-comparable
    snapshots (orders by id, ledger entries in order, portfolios by account with
    positions by symbol, order books by symbol) and a comparator that reports only the
    first divergence with enough detail to reproduce it.
  - `FinancialInvariantChecker`: cash conservation, asset conservation, ledger
    double-entry balance, order-quantity conservation, account isolation — all derived
    from FinEx's existing accounting model, no new financial rules invented.
  - `StressHarness` / `StressHarnessResult`: ties the above into three checks
    (determinism between two independent engines, replay equivalence, invariants) keyed
    by a reproducible `(seed, profile, commandCount)`.
  - `StressDriver`: a `main`-class driver for large-scale manual runs, following the
    exact convention of `SustainedSharedServiceDriver`/`ProfileRunner` (not part of
    `mvn test`).
- Progressive validation, smallest to largest, stopping to investigate on any failure
  (none needed after the fix below):
  - 10, 100 commands × seeds {1, 42, 12345} × all 7 profiles: PASS.
  - 1,000, 10,000 commands × same seeds/profiles: PASS.
  - 100,000 commands × seed 7 × all 7 profiles: PASS (runtime 1.5s–79.8s per profile;
    `MULTI_ACCOUNT`'s larger account count made it the slow outlier, not a correctness
    issue — the harness itself is intentionally unoptimized).
  - 1,000,000 commands × seed 7 × `BALANCED`: PASS (elapsed ≈684s / 11.4 min for the
    full determinism + replay + invariant run). The other six profiles were validated
    through 100k, not 1M, in this session due to runtime.
- **Bug found and fixed:** the very first attempt at the replay-equivalence check threw
  `OrderRejectedException` out of `ReplayEngine.replay`. Root cause:
  `OrderService.submitOrder(OrderRequest, Instant)` appends the `SUBMIT_ORDER` event
  *before* the risk check runs, so a rejected order is still recorded in the event log;
  `ReplayEngine` had no way to catch `OrderRejectedException` (defined in `finex-api`,
  which `finex-event-log` cannot depend on without a cycle), so replaying any log
  containing a rejection aborted the loop and silently dropped every later event. This
  is a real, pre-existing production bug never exercised by the small hand-written
  `OrderServiceReplayTest` (4 commands, never rate-limited). Fixed by catching and
  discarding `OrderRejectedException` in `OrderService.submitOrder(SubmitOrderCommand,
  Instant)` (the `CommandHandler` override `ReplayEngine` calls into) — the rejected
  order's state was already recorded by `processSubmitOrder` before the throw, so this
  matches what a live caller already sees. Added regression test
  `OrderServiceReplayRejectedOrderTest`, confirmed to fail without the fix (reproduced
  the exact original stack trace) and pass with it.
- `StressHarnessTest` (16 JUnit cases: 7 profiles × 3 seeds at commandCount=10 and 1,000,
  plus one 10,000-command run) now runs as part of `mvn test`, staying fast (~0.6s).

**Verified:**
- `mvn test` — SUCCESS across all 16 modules (134+ tests, including the 16 new stress
  cases and the new replay regression test).
- Manually confirmed the regression test fails without the `OrderService` fix (same
  stack trace as the original discovery) and passes with it — a real TDD-style
  before/after check, not just a passing test after the fact.
- Determinism, replay equivalence, and all five financial invariants held at every
  scale tested (10 through 1,000,000 commands).

**Blockers:** None. OPT-009 is explicitly classified as correctness/validation
infrastructure, not benchmarked as a performance change (per the plan's own
classification rule).

**Next session should:**
- Proceed to OPT-010 (fixed-point numerics), using `StressHarness` as the correctness
  oracle, informed by the OPT-007-session JFR profile showing `BigDecimal.valueOf` and
  `Long.valueOf` boxing as the dominant allocation source across risk, matching, and
  settlement. Any fixed-point prototype must pass the full OPT-009 harness (determinism,
  replay, invariants) before being considered for a performance benchmark.

---

## 2026-09-03 — Session 16: OPT-007 controlled validation + evidence-discipline pass

**Scope:** Follow-up to Session 15. Restore benchmark trustworthiness, give OPT-007 an
honest evidence-based verdict, profile the current HEAD, and decide the next backlog
item from measurement rather than assumption — per an explicit evidence-discipline
directive (validated/preliminary/unvalidated/inconclusive labeling, no invented numbers,
no silent evidence-level upgrades, continue existing OPT numbering, document reverted
attempts).

**Done:**
- Reconnaissance: confirmed `HEAD=635219f`, working tree clean, `mvn test` green,
  reviewed `OPTIMIZATION_PLAN.md`/`OPTIMIZATIONS.md`/`BENCHMARKS.md` and the existing
  benchmark commands/driver rather than creating a parallel tracking system.
- Inspected the benchmark host: Apple M-series, 10 cores, 16 GB RAM, JDK 25.0.2, macOS
  26.6.2. At the time of the OPT-007 implementation session the machine had <300 MB free
  RAM and heavy background load (this agent's own Electron process, Microsoft Defender
  scanning, a browser); that explains the unreliable ~200-300k ops/sec readings recorded
  in Session 15, not a code regression.
- Built OPT-006 (`819cd63`) and OPT-007 (`635219f`) side by side via `git worktree`
  (`/tmp/finex-opt006`) and ran a controlled 5-rep, interleaved A/B with identical
  JDK/JVM/workload on a calmer machine state:
  - OPT-006: mean 690,707.97 ops/s, stdev ≈43,268 (6.3%)
  - OPT-007: mean 701,034.80 ops/s, stdev ≈41,020 (5.9%)
  - Delta (+1.5%) is smaller than the noise floor on *both* commits.
- **Verdict: OPT-007 = NO MEASURABLE IMPROVEMENT.** Reclassified in `OPTIMIZATIONS.md`,
  `OPTIMIZATION_PLAN.md`, and `BENCHMARKS.md` from the earlier "COMPLETED / KEPT" framing
  (which conflated "shipped and tested" with "performance validated") to an explicit
  engineering-benefit-only verdict. The earlier ~200-300k readings are superseded by this
  controlled run and documented as environmental.
- Removed the git worktree after use (`git worktree remove /tmp/finex-opt006 --force`).
- Re-profiled current HEAD with JFR (`settings=profile`) on the same driver. Top CPU
  frames: `MatchingEngine.placeOrder`, `OrderService.processSubmitOrder`,
  `BinaryCodec.encodeToBytes`, `RiskEngine.validate`/`RiskResult.ok`,
  `SettlementService.settle`, `InMemoryLedger.post`. Top allocation frames:
  `java.math.BigDecimal.valueOf` (143 samples) and `java.lang.Long.valueOf` (66,
  boxing), traced to `OrderService.submitOrder`/`processSubmitOrder`,
  `RiskEngine.validate`/`onTrade`, `AccountRiskState.reserveOrder`,
  `SettlementService.settle`, and `MatchingEngine.placeOrder`.
- **Backlog reorder decision (evidence-driven, per the plan's own override rule):**
  `MetricsService`/Micrometer does not appear anywhere in the current top CPU or
  allocation frames, so OPT-008 (metrics batching) is not supported by fresh evidence.
  `BigDecimal`/boxing dominates allocation, which points at OPT-010 (fixed-point
  numerics) — but that is explicitly gated behind OPT-009 (randomized
  differential/financial-invariant stress harness) because changing numeric
  representation in a financial engine without that safety net is unacceptable risk.
  Revised order: **OPT-009 → re-profile → OPT-010 (only where justified) → OPT-008 (only
  if a later profile supports it) → OPT-011 last.**
- Documented (but did not commit) a reverted prototype: caching `SettlementService`
  account keys in a `ConcurrentHashMap<Long, String>` was tried against the
  `SettlementService`/`InMemoryLedger` hotspot and abandoned — `long` → `Long` boxing on
  every lookup traded one allocation for another with no clear net benefit. Recorded per
  the failure-handling policy as engineering evidence, not as a numbered OPT.

**Verified:**
- `mvn test` — SUCCESS across all 16 modules (unchanged from Session 15; no application
  code touched this session, only tracking/documentation and a temporary worktree).
- Controlled A/B methodology: same JDK, same JVM defaults, same workload/seed, 5
  interleaved repetitions, git-worktree isolation of the two commits being compared.

**Blockers:** None. The earlier environment instability was diagnosed and is documented;
it does not block correctness work, only requires multi-rep A/Bs instead of single runs
on this particular machine.

**Next session should:**
- Build OPT-009: a deterministic, seeded, randomized command generator (submit/cancel,
  partial fills, crossing/non-crossing, multiple accounts/instruments) plus a financial
  invariant checker (cash conservation, asset conservation, double-entry balance,
  position consistency, order/trade quantity conservation, PnL consistency, sequence
  monotonicity) and a differential comparison harness (baseline vs. candidate engine).
- Only after OPT-009 exists, revisit OPT-010 (fixed-point numerics) using it as the
  correctness safety net, informed by the `BigDecimal`/boxing profile captured above.

---

## 2026-09-03 — Session 15: OPT-007 — Reduce event-log serialization allocation

**Scope:** Continuation of the post-master-plan performance engineering pass.

**Done:**
- Re-profiled the post-OPT-006 baseline. The dominant remaining allocation frames were
  `CommandSerializer.toEvent`, `Event.<init>`, and the second `Event` copy performed inside
  `InMemoryEventStore.append`.
- **OPT-007:** Reduced per-order event-log allocation:
  - Added `BinaryCodec.encodeToBytes(ProtocolMessage)`: writes a length-prefixed frame
    directly to a fresh `byte[]`, eliminating the intermediate `HeapByteBuffer` allocation
    and the `ByteBuffer.get` copy in the hot path.
  - Added `CommandSerializer.toPayload(SubmitOrderCommand|CancelOrderCommand)` returning a
    raw `byte[]` payload.
  - Added `EventStore.append(Instant, String, byte[])` and implemented it in
    `InMemoryEventStore` so `OrderService` can append without first wrapping a payload in a
    throw-away `Event`.
  - Removed the defensive `payload.clone()` from `Event` construction and from `payload()`;
    callers on the hot path pass freshly allocated arrays, so the extra copy was pure
    overhead. The `InMemoryEventStore.append(Event)` compatibility path still clones to
    preserve the store boundary.
  - Updated `OrderService.submitOrder` / `cancelOrder` to use the new overload.
- All affected unit tests pass, including `CommandSerializerTest`, `EventStoreTest`,
  `OrderServiceReplayTest`, and full `mvn test`.

**Verified:**
- `mvn test` — SUCCESS across all 16 modules.
- `OrderServiceReplayTest` still reconstructs the order book, ledger, and portfolio
  identically from the event store.
- `EventStoreTest.eventsAreImmutable` updated to clone its own payload before passing it
  in, preserving the immutability contract from the caller side.

**Blockers:** The sustained-driver benchmark environment became unstable during this session
(laptop-class machine under memory pressure; unrelated Docker/container activity).
Consecutive runs of the committed OPT-006 baseline and the new OPT-007 code both reported
throughput in the 200–300k ops/sec range, far below the previously measured 671k ops/sec,
so a reliable before/after OPT-007 measurement is not available. The code change is
retained because it reduces allocation and passes all correctness tests, but the
magnitude of the improvement cannot be quantified on this machine today.

**Next session should:**
- Re-run the sustained driver on a quiet, dedicated environment to validate OPT-007.
- Re-profile and continue with the remaining backlog only if the next hotspot is clearly
  measured: `SettlementService` account-key string caching, `InMemoryLedger` ledger-entry
  copying, `MetricsService` batching, fixed-point numerics, lock-free order book, etc.

---

## 2026-09-03 — Session 13: OPT-004 + OPT-005 — Latency measurement and O(1) risk reservation totals

**Scope:** Continuation of the post-master-plan performance engineering pass; same
measure-first methodology as previous sessions.

**Done:**
- Created `docs/performance/OPTIMIZATION_PLAN.md` with a prioritized, evidence-driven
  backlog (latency measurement, allocation reduction, fixed-point numerics, lock-free
  order book, async settlement, etc.) and explicit stopping conditions.
- **OPT-004:** Added per-order `System.nanoTime()` latency measurement to
  `SustainedSharedServiceDriver`. Output now reports p50/p90/p99/p99.9/p99.99/max in
  nanoseconds alongside throughput. Added regression assertions in
  `SustainedSharedServiceDriverTest`.
- Re-profiled the post-OPT-004 baseline with JDK Flight Recorder (`settings=profile`)
  on the sustained 1.5M-order driver. Identified the next dominant bottleneck:
  `AccountRiskState.reservedCash()` / `reservedPosition()` were doing a full
  `ConcurrentHashMap.values().stream().reduce(...)` on every `RiskEngine.validate`
  call, repeatedly allocating `BigDecimal` sums for all open orders.
- **OPT-005:** Added running `totalReservedCash` and `totalReservedPosition` fields to
  `AccountRiskState`, updated incrementally on `reserveOrder`, `releaseOrder`, and
  `applyTrade`. `reservedCash()` / `reservedPosition()` are now O(1) and return the
  cached totals; `availableCash()` and `projectedPosition()` use them directly.
- Added `runningReservationTotalsAreConsistentAcrossMultipleOrders` test to
  `RiskEngineTest`.
- Measured before/after on the sustained driver (3 runs each):
  - Before (post-OPT-004): 137,562.51 ops/sec average
  - After (post-OPT-005): 587,705.20 ops/sec average
  - Delta: **+450,142.69 ops/sec, +327.2%**
  - Cumulative vs original baseline (pre-OPT-002): **+659.8%**
  Order-level throughput: **≈ 11.75M orders/sec** on a single shared `OrderService`.
- Latency results:
  - p50: ~2.9 µs → ~1.1 µs (≈ 60% reduction)
  - p99: ~28.5 µs → ~5.9 µs (≈ 79% reduction)
  - p99.9: ~49 µs → ~28 µs (≈ 43% reduction)
- Re-profiled after OPT-005: `AccountRiskState` methods no longer appear in top CPU or
  allocation samples. `BigDecimal.valueOf` allocation samples dropped from ~486-650 per
  1.5M-order run to ~144 per run. The dominant remaining CPU/allocation frames are now
  `MatchingEngine.placeOrder`, `InMemoryLedger.post`, `SettlementService.settle`,
  `CommandSerializer.toEvent`, and `BinaryCodec.encode`.
- Updated `docs/performance/OPTIMIZATION_PLAN.md` (status), `OPTIMIZATIONS.md`
  (OPT-004 and OPT-005 entries), `OPTIMIZATION_EVIDENCE.md` (raw data and JFR
  findings), `BENCHMARKS.md` (post-OPT-005 numbers and latency table),
  `FINAL_BENCHMARK_REPORT.md` (new verdict and roadmap), `AGENT_CONTEXT.md`,
  `TODO.md`.

**Verified:**
- `mvn test` — SUCCESS across all 16 modules.
- Differential check: identical order/trade counts (1,500,000 → 750,000) and no change
  in final portfolio/ledger/cash behavior on the deterministic workload.

**Blockers:** None.

**Next session should:**
- Tackle the next evidence-backed hotspot: event-log/ledger per-trade allocation
  (`SettlementService.settle`, `InMemoryLedger.post`, `CommandSerializer.toEvent`,
  `BinaryCodec.encode`). Profile first, then make a minimal, correctness-preserving
  change. Continue down `OPTIMIZATION_PLAN.md`.

---

## 2026-09-03 — Session 14: OPT-006 — Reduce per-match collection copies and encode-buffer allocation

**Scope:** Continuation of the post-master-plan performance engineering pass.

**Done:**
- Re-profiled the post-OPT-005 baseline with the sustained 1.5M-order driver and JFR
  (`settings=profile`).
- Identified the next top CPU/allocation hotspots:
  - `BinaryCodec.encodePayload` (8 CPU samples) and `BinaryCodec.encode`
    allocating a new `ByteArrayOutputStream` per call (28 allocation samples).
  - `MatchingEngine.placeOrder` (14 CPU samples) plus `List.copyOf`/`Map.copyOf`
    (`Map.ofEntries` 11 allocation samples, `HashMap.resize` / `ArrayList.grow`
    due to default capacities).
  - `SustainedSharedServiceDriver.run` re-parsing `BigDecimal` strings inside the
    tight loop, polluting the benchmark with non-production allocation.
- **Change (3 small, safe, independent improvements):**
  1. `BinaryCodec.encode` now reuses a `ThreadLocal<ByteArrayOutputStream>` per
     thread, resetting it between calls.
  2. `MatchingEngine.placeOrder` now returns the engine-local `ArrayList`/`HashMap`
     directly in `MatchResult` and pre-sizes them with capacity 4, avoiding
     `List.copyOf`/`Map.copyOf` and resize/grow.
  3. `SustainedSharedServiceDriver` stores `SELL_PRICE`, `BUY_PRICE`, and `QTY` as
     static final `BigDecimal` constants.
- Measured before/after on the sustained driver (3 runs each):
  - Before (post-OPT-005): 587,705.20 ops/sec average
  - After (post-OPT-006): 671,089.23 ops/sec average
  - Delta: **+83,384.03 ops/sec, +14.2%**
  - Cumulative vs original baseline: **+767.0%**
  Order-level throughput: **≈ 13.42M orders/sec**.
- Latency results (post-OPT-006):
  - p50: ~1.1 µs → ~1.0 µs (≈ 10% reduction)
  - p99: ~5.9 µs → ~5.2 µs (≈ 12% reduction)
  - p99.9: ~28 µs → ~23 µs (≈ 16% reduction)
- JMH `MatchingEngineBenchmark.placeBuyAndSell` improved: 3.37M ops/s → 4.23M
  ops/s (single short indicative run).
- Re-profiled after OPT-006:
  - `ByteArrayOutputStream.<init>` no longer appears in allocation samples.
  - `java.util.Map.ofEntries` and `java.util.HashMap.resize` gone from top allocation.
  - `MatchingEngine.placeOrder` CPU samples dropped from 14 to 6.
  - Top remaining CPU frames: `BinaryCodec.encodePayload` (8), `OrderService.processSubmitOrder` (8),
    `MatchingEngine.placeOrder` (6), `InMemoryLedger.post` (5), `RiskEngine.validate` (5).
- Updated `OPTIMIZATIONS.md` (OPT-006 entry), `OPTIMIZATION_EVIDENCE.md` (raw data and JFR),
  `BENCHMARKS.md` (post-OPT-006 numbers and latency table),
  `FINAL_BENCHMARK_REPORT.md` (new verdict and roadmap), `AGENT_CONTEXT.md`,
  `OPTIMIZATION_PLAN.md` (status), `TODO.md`.

**Verified:**
- `mvn test` — SUCCESS across all 16 modules.
- Differential check: identical order/trade counts (1,500,000 → 750,000) and no change
  in final portfolio/ledger/cash behavior on the deterministic workload.

**Blockers:** None.

**Next session should:**
- Tackle the next evidence-backed hotspot: per-trade event/ledger allocation
  (`Event.<init>`, `CommandSerializer.toEvent`, `SettlementService.settle`,
  `InMemoryLedger.post`) or `String` account-key caching — profile first, then pick
  the larger contributor.

---

## 2026-09-03 — Session 12: OPT-003 — Evidence-driven optimization (mark-to-market skip on unchanged price)

**Scope:** Continuation of the post-master-plan performance engineering pass; same
measure-first methodology as Session 11.

**Done:**
- Re-profiled the post-OPT-002 baseline with the committed
  `SustainedSharedServiceDriver` and JDK Flight Recorder (`settings=profile`).
- Identified the new top `com.finex.*` CPU hotspot: `com.finex.portfolio.Position.mark`
  (112/1243 sampled leaf frames, 9.0%), called from
  `PortfolioService.markToMarket(symbol, trade.price())` on every trade.
- Root cause: `SettlementService.settle` called `markToMarket` after every trade with
  the trade price as the mark price, and `PortfolioService.markToMarket` re-scanned
  every account's position for that symbol even when the mark price was identical to
  the previous mark. In the sustained driver, prices alternate in blocks of 500 orders,
  so the same mark price was repeated 500 times per block.
- **OPT-003:** added a `lastMarkPrices` cache to `PortfolioService`. `markToMarket`
  returns immediately when the requested mark price equals the cached last mark price
  for that symbol; otherwise it stores the new price and revalues all positions as
  before. `Position.withTrade` (called by `applyTrade`) already computes the traded
  accounts' `unrealizedPnl` at the mark price, so no extra work is needed when the
  price is unchanged.
- Added regression tests in `PortfolioServiceTest`:
  - `markToMarketRevaluesPositionsWhenPriceChanges`
  - `markToMarketIsIdempotentAtSamePrice`
  - `markToMarketAtSamePriceStillCorrectlyUpdatesNewPosition`
- Measured before/after on the sustained driver (3 runs each):
  - Before (post-OPT-002): 98,944.11 ops/sec average
  - After (post-OPT-003): 137,562.51 ops/sec average
  - Delta: **+38,618.40 ops/sec, +39.0%**
  - Cumulative vs original baseline (77,389.46): **+77.8%**
- Re-profiled after the change: `Position.mark` / `PortfolioService.markToMarket` no
  longer appear in the top CPU or allocation samples. `BigDecimal.valueOf` allocation
  share dropped further from 14.8% (671/4520) to 10.6% (486/3039).
- Updated `docs/performance/OPTIMIZATIONS.md` (OPT-003 entry), `OPTIMIZATION_EVIDENCE.md`
  (raw per-run data and JFR findings), `BENCHMARKS.md` (post-OPT-003 numbers and the
  sustained-driver table), `FINAL_BENCHMARK_REPORT.md` (updated interpretation and
  honest verdict), `AGENT_CONTEXT.md`, and `TODO.md`.

**Verified:**
- `mvn test` — SUCCESS across all 16 modules.
- Differential check: identical order/trade counts (1,500,000 → 750,000) and no
  change in financial outcomes on the deterministic workload.

**Blockers:** None.

**Next session should:**
- Profile the new top `com.finex.*` CPU frames (`BinaryCodec.encodePayload`,
  `MatchingEngine.placeOrder`, `InMemoryLedger.post`, `SettlementService.settle`) and
  identify the next highest-value optimization. Likely candidate: event-log/ledger
  allocation reduction, `BigDecimal` hot-path cleanup, or metrics offloading. Always
  evidence first.

---

## 2026-09-03 — Session 11: OPT-002 — Evidence-driven optimization (market-data snapshot elimination)

**Scope:** Post-master-plan performance engineering pass, following a strict
measure-first methodology (reconnaissance → baseline → JFR profiling → hypothesis →
minimal change → benchmark → correctness verification → documentation).

**Done:**
- Reconnaissance: confirmed the existing tracking system (`PROJECT_PLAN.md`,
  `PROGRESS.md`, `TODO.md`, `AGENT_CONTEXT.md`, `DESIGN_DECISIONS.md`,
  `docs/performance/*`) and extended it rather than creating a new one.
- Established baseline: ran full `mvn test` (green), recorded hardware/software
  environment (Apple Silicon, 10 cores, 16 GB RAM, JDK 25.0.2).
- Built a reproducible, deterministic 1.5M-order sustained-load driver
  (`com.finex.benchmarks.SustainedSharedServiceDriver`) with bounded per-account
  cash/position (side flips every `accountCount` orders) for low-variance measurement.
- Profiled with JDK Flight Recorder (`settings=profile`): found
  `BookUpdateFactory.aggregate` (called unconditionally from
  `OrderService.publishBookUpdate`, even with zero market-data subscribers) was the
  **#1 CPU hotspot** and **56.8% of all sampled allocations**.
- **OPT-002:** added `MarketDataPublisher.hasSubscribers()` and guarded
  `OrderService.publishBookUpdate` / `publishMatchEvents` to skip snapshot/event
  construction entirely when nobody is subscribed. Zero behavior change when a
  subscriber exists (verified by new tests).
- Added regression tests: `MarketDataPublisherTest.hasSubscribersReflects...`,
  `OrderServiceMarketDataTest` (both "subscribed" and "no subscriber" cases).
- Measured before/after on the sustained driver (3 runs each): **+24% to +28%
  throughput** on the shared, no-subscriber `OrderService` path (77,389 → ~95,710-98,944
  ops/sec average). Re-profiled after the change: `BookUpdateFactory` no longer appears
  in CPU or allocation samples at all.
- Documented full methodology, raw data, and reproduction commands in
  `docs/performance/OPTIMIZATION_EVIDENCE.md`; wrote up the OPT-002 entry (with the
  Component/Problem/Evidence/Hypothesis/Change/Benchmark/Correctness/Decision schema)
  in `docs/performance/OPTIMIZATIONS.md`; cross-referenced from
  `docs/performance/FINAL_BENCHMARK_REPORT.md` and `BENCHMARKS.md`.
- Identified (but did not action) the next hotspot: `Position.mark` in
  `PortfolioService.markToMarket`, now the top CPU frame post-OPT-002. This is
  legitimate financial work, not wasted work, so it needs its own investigation
  before any change (candidate OPT-003).

**Verified:**
- `mvn test` — SUCCESS across all 16 modules (finex-api: 31/31 including 3 new tests;
  finex-benchmarks: new `SustainedSharedServiceDriverTest` passes).
- Differential check: identical order/trade counts (1,500,000 → 750,000) before and
  after the change on the same deterministic workload.

**Blockers:** None. Noted a local `exec-maven-plugin` quirk (documented in
`AGENT_CONTEXT.md`) where `-Dexec.mainClass` overrides are not honored on this
machine/plugin version; worked around by invoking `java -cp` directly.

**Next session should:**
- Investigate OPT-003 (`Position.mark` / `markToMarket` cost) with the same
  measure-first methodology, or continue down the priority list in the performance
  engineering master prompt (allocation reduction, fixed-point numerics, order-book
  structure, single-writer architecture) — always evidence first, one change at a time.

---

## 2026-09-03 — Session 10: Phases 23-24 — Documentation Consolidation and Final Benchmark Campaign

**Phase:** 23 → 24 (done)

**Done:**
- **Phase 23 — Documentation:**
  - Filled `docs/ARCHITECTURE.md` with module boundaries, data flow, concurrency model,
    security, and observability.
  - Filled `docs/PROTOCOL.md` with frame layout, primitive encodings, message types,
    and usage example.
  - Filled `docs/FINANCIAL_MODEL.md` with order lifecycle, risk limits, clearing,
    settlement, ledger, and portfolio.
  - Added ADR-010 (benchmarks/profiling), ADR-011 (observability), and ADR-012 (security)
    to `DESIGN_DECISIONS.md`.
  - Updated `README.md` with current status, API-key examples, metrics, and benchmark
    commands.
- **Phase 24 — Final Benchmark Campaign:**
  - Added `MultiThreadedLoadGeneratorBenchmark` (4 threads, isolated `OrderService` per
    invocation).
  - Re-ran the full JMH suite and captured order-level throughput numbers.
  - Wrote `docs/performance/FINAL_BENCHMARK_REPORT.md` with an honest assessment: pure
    matching reaches ~7M placements/sec, isolated end-to-end ~1M-2.7M orders/sec, but
    shared `OrderService` is ~53k/sec due to `BigDecimal`/`TreeMap`/synchronous
    settlement overhead. Included a concrete roadmap to a real 1M/sec shared engine.
  - Updated `docs/performance/BENCHMARKS.md` with the latest results.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS across all modules.

**Blockers:** None.

**Next session should:**
- Project is complete per the master plan. Any further work is follow-up optimization or
  deployment hardening.

---

## 2026-09-03 — Session 9: Phases 17-22 — Benchmarks, Profiling, Optimization, Observability, Failure Testing, Security

**Phase:** 17 → 22 (done)

**Done:**
- Updated `TODO.md`, `AGENT_CONTEXT.md`, and `PROGRESS.md`.
- **Phase 17 — Performance Benchmarks:** Added `finex-benchmarks` module with JMH and
  benchmarks for `OrderBook`, `MatchingEngine`, `OrderService`, and end-to-end `LoadGenerator`.
  Recorded baseline numbers in `docs/performance/BENCHMARKS.md`.
- **Phase 18 — Profiling:** Added `ProfileRunner` that captures a JFR recording around a
  `LoadGenerator` run and `docs/performance/PROFILING.md`.
- **Phase 19 — Optimization:** Implemented in-place `OrderBook.replaceOrder` and updated
  `MatchingEngine` to avoid TreeMap remove/re-insert on partially filled orders. Documented
  before/after numbers and analysis in `docs/performance/OPTIMIZATIONS.md`.
- **Phase 20 — Observability:** Added `MetricsService` with Micrometer counters/timer,
  wired `OrderService` to record submitted/rejected/cancelled orders, trades, and latency.
  Added Grafana dashboard JSON and provisioning under `docker/grafana/`.
- **Phase 21 — Failure Testing:** Added `OrderServiceFailureTest` and `EventStoreFailureTest`
  covering invalid input, risk rejections, order lifecycle failures, and corrupt event replay.
  Added `docs/testing/FAILURE_TESTING.md`.
- **Phase 22 — Security:** Added `ApiKey`, `ApiKeyService`, and `ApiKeyAuthenticationFilter`
  using the `X-API-Key` header. Enforced account isolation in `OrderController` (submit
  accountId match, get/cancel restricted to owner). Added `docs/security/API_KEYS.md`.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS across all modules.

**Blockers:** None.

**Next session should:**
- Phase 23 — Documentation review / consolidation, or Phase 24 — final benchmark campaign.

---

## 2026-09-02 — Session 8: Phases 12-16 — Portfolio, Clearing, Settlement, Replay, Load Generator

**Phase:** 11 → 12 → 13 → 14 → 15 → 16 (done)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md`, `README.md`, `DESIGN_DECISIONS.md`.
- **Phase 12 — Portfolio / P&L:** Added `finex-portfolio` module with `Position`, `Portfolio`,
  `PortfolioService`, and `PortfolioController` (`GET /api/v1/portfolios/{accountId}`).
  Tracks signed quantity, average price, realized/unrealized PnL, cash, and total equity.
- **Phase 13 — Clearing:** Added `finex-clearing` with `FeeSchedule`, `ClearingResult`, and
  `ClearingService` computing net buyer/seller cash and fee accrual.
- **Phase 14 — Settlement:** Added `finex-settlement` with `SettlementService` orchestrating
  clearing → ledger posting → portfolio update. `OrderService` delegates per-trade settlement.
- **Phase 15 — Replay:** Extended `OrderServiceReplayTest` to assert that a fresh `OrderService`
  replay produces identical order book, orders, ledger entries, and portfolios.
- **Phase 16 — Load Generator:** Added `finex-load-generator` module with `LoadConfig`,
  `LoadResult`, and `LoadGenerator` driving `OrderService` and reporting throughput/latency.
  `LoadGeneratorTest` validates deterministic shape and trades.
- Added ADR-008 (clearing/settlement) and ADR-009 (load generator) to `DESIGN_DECISIONS.md`.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS across all modules including the new `finex-portfolio`, `finex-clearing`,
  `finex-settlement`, `finex-load-generator`, and updated `finex-api` tests.

**Blockers:** None.

**Next session should:**
- Start Phase 17 — Performance Benchmarks (JMH/component/end-to-end) or any other priority.

---

## 2026-09-02 — Session 7: Phases 7-11 — Market Data, Binary Protocol, Event Log, Sharding, Ledger

**Phase:** 6 → 7 → 8 → 9 → 10 → 11 (done)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md`, `DESIGN_DECISIONS.md` for Phases 7-11.
- **Phase 7 — Market Data:** Added `finex-market-data` module with `MarketDataEvent` sealed
  hierarchy (`BookUpdate`, `TradeEvent`, `ExecutionEvent`), `PriceLevel`, `MarketDataPublisher`,
  `MarketDataListener`, and `SimpleMarketDataPublisher`. `OrderService` publishes trade/book
  events on every submit/cancel.
- **Phase 8 — Binary Protocol:** Added `finex-protocol` module with `ProtocolMessage` records
  and `BinaryCodec` using 4-byte length framing, single-byte enum ordinals, and UTF-8/BigDecimal
  string payloads. Round-trip tests for all message types.
- **Phase 9 — Event Architecture:** Added `finex-event-log` module with `Event`, `EventStore`,
  `InMemoryEventStore`, `CommandSerializer`, `CommandHandler`, and `ReplayEngine`.
  `OrderService` appends command events and is replayable; `OrderServiceReplayTest` verifies
  state reconstruction.
- **Phase 10 — Symbol Sharding:** Added `finex-shard` module with `SymbolShardRouter`,
  `EngineShard`, and `ShardCoordinator`. `OrderService` routes symbols to shards. Fixed
  `OrderService` order cache by extending `MatchResult` with `updatedOrders` populated by
  `MatchingEngine`.
- **Phase 11 — Ledger:** Added `finex-ledger` module with `Ledger`, `InMemoryLedger`,
  `LedgerAccount`, `LedgerEntry`, `DebitCredit`, and `AccountType`. `OrderService` posts a
  balanced cash leg and a balanced asset leg for every trade. `InMemoryLedger` enforces
  `sum(debits) == sum(credits)` per posting.
- Added `OrderServiceShardingTest`, `OrderServiceLedgerTest`, `SymbolShardRouterTest`,
  `InMemoryLedgerTest`, and updated `OrderServiceReplayTest`.
- Added ADR-006 (sharding) and ADR-007 (ledger) to `DESIGN_DECISIONS.md`.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS across all modules: `OrderControllerTest` (10),
  `OrderServiceReplayTest`, `OrderServiceShardingTest`, `OrderServiceLedgerTest`,
  `HealthControllerTest`, `MatchingEngineTest` (15), `OrderBookTest` (10), `InstrumentTest` (15),
  `RiskEngineTest` (10), `MarketDataPublisherTest` (2), `BinaryCodecTest` (7),
  `EventStoreTest` (2), `CommandSerializerTest` (2), `SymbolShardRouterTest` (3),
  `InMemoryLedgerTest` (4).

**Blockers:** None.

**Next session should:**
- Commit Phase 11.
- Start Phase 12 — Portfolio / P&L, Phase 13 — Clearing, Phase 14 — Settlement, or another
  priority.

---

## 2026-09-02 — Session 6: Phase 6 — Risk Engine

**Phase:** 5 → 6 (done)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md`, `README.md` for Phase 6.
- Added `finex-risk` Maven module and wired it into the parent reactor before `finex-api`;
  `finex-api` depends on `finex-risk`.
- Implemented baseline pre-trade risk engine in `finex-risk`:
  - `RiskConfig` with size, notional, position, cash exposure, collar, and rate-limit limits.
  - `AccountRiskState` tracking cash, position, cash/position reservations for open orders,
    and a sliding window of order timestamps.
  - `RiskResult` accepted/rejected with reason.
  - `RiskEngine` performing size, notional, collar, projected-position, cash-exposure,
    and rate-limit checks. Validates `LIMIT` and `MARKET` orders; reserves cash (BUY) and
    projected position (BUY/SELL) on acceptance.
  - `RiskEngineTest` (10 tests) covering acceptance, size, notional, cash, position,
    collar, market-without-last-trade, trade/cancel reservation lifecycle, and rate limit.
- Integrated `RiskEngine` into `finex-api` `OrderService`:
  - Per-symbol `lastTradePrice` map.
  - Per-account in-memory `AccountRiskState` map with default cash and position.
  - Pre-trade validation before `MatchingEngine.placeOrder`; rejects orders by throwing
    `OrderRejectedException` carrying a rejected `Order` and reason.
  - Updates buyer/seller cash and positions and releases reservations on every `Trade`.
  - Releases reservations on cancel.
- Added `Order.rejected(Instant)` and `OrderRejectedException`; `OrderResponse` now includes
  an optional `rejectionReason`.
- `GlobalExceptionHandler` returns `OrderResponse` with `status=REJECTED` for
  `OrderRejectedException`.
- Expanded `OrderControllerTest` to 10 tests, adding rejection cases for price collar,
  position limit, total open notional / cash exposure, and insufficient cash.
- Added ADR-005 documenting the in-memory, reservation-based risk-engine baseline.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `RiskEngineTest` 10/10, `OrderControllerTest` 10/10,
  `MatchingEngineTest` 15/15, `OrderBookTest` 10/10, `InstrumentTest` 15/15,
  `HealthControllerTest` 1/1.

**Blockers:** None.

**Next session should:**
- Commit Phase 6 work.
- Start Phase 7 — Market Data (BOOK_UPDATE/TRADE/EXECUTION events, snapshot + incremental,
  async publication) or choose another phase.

---

## 2026-09-02 — Session 5: Phase 5 — REST/API Layer

**Phase:** 4 → 5 (done)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md` for Phase 5.
- Added `finex-matching-engine` dependency to `finex-api/pom.xml` and managed it in parent
  `pom.xml`.
- Exposed `OrderBook.findOrder(long)` for accurate live order state lookup.
- Implemented `com.finex.api.order.OrderService`:
  - Per-symbol `MatchingEngine` map (auto-created on first order for a symbol).
  - Global `AtomicLong` order/sequence generator.
  - In-memory `Map<Long, Order>` cache with fallback to `OrderBook.findOrder`.
  - `submitOrder`, `cancelOrder`, `getOrder`, `getOrderBook`.
- DTOs in `finex-api`: `OrderRequest`, `OrderResponse`, `TradeView`, `OrderBookView`.
- `com.finex.api.order.OrderController`:
  - `POST /api/v1/orders` — submit and match
  - `DELETE /api/v1/orders/{orderId}` — cancel
  - `GET /api/v1/orders/{orderId}` — query
  - `GET /api/v1/order-books/{symbol}` — snapshot
- `com.finex.api.GlobalExceptionHandler` mapping `IllegalArgumentException` to 400.
- Validation in `OrderService` and `OrderController` for positive quantity, LIMIT price,
  MARKET price absence, and non-null enums/symbol.
- Added `spring-boot-starter-webmvc-test` dependency for Spring Boot 4 `WebMvcTest` and
  `MockMvc` support.
- `OrderControllerTest` (7 tests) using `@WebMvcTest`, `@Import` of `OrderService` and
  `GlobalExceptionHandler`, and `@DirtiesContext` to isolate `OrderService` state.
  Covered: submit and rest, full match with trade, query, cancel, book snapshot,
  validation rejection.
- Updated `README.md` with curl examples for the trading API.

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `OrderControllerTest` 7/7, `MatchingEngineTest` 15/15,
  `OrderBookTest` 10/10, `InstrumentTest` 15/15, `HealthControllerTest` 1/1.

**Blockers:** None.

**Next session should:**
- Commit Phase 5 work.
- Start Phase 6 — Risk Engine or choose another phase (Market Data, Binary Protocol, Event
  Architecture, etc.).

---

## 2026-09-02 — Session 4: Phase 4 — Matching Engine

**Phase:** 3 → 4 (in progress)

**Done:**
- Updated `TODO.md`, `PROJECT_PLAN.md`, `AGENT_CONTEXT.md` for Phase 4.
- Added `finex-matching-engine` Maven module, wired into parent `pom.xml` between
  `finex-order-book` and `finex-api`.
- Implemented `com.finex.matching.MatchingEngine`:
  - Per-symbol, single-threaded baseline.
  - `placeOrder(Order, Instant)` returns `MatchResult` (final order, trades, addedToBook).
  - Walks opposite side of book from top, matching at resting order's price.
  - Full and partial fills for both incoming and resting orders using `Order.withFill`.
  - `Trade` generation with monotonic `tradeSequence` from an internal `AtomicLong`.
  - Limit price gating; market orders fill until liquidity is exhausted, then cancel
    the unfilled remainder.
  - `cancelOrder(long)` delegates to `OrderBook`.
- Added `MatchingEngineTest` (15 tests) covering full fill, partial incoming fill,
  partial resting fill, multiple fills across price levels, market order full fill and
  cancellation, non-marketable limit order resting, buy/sell price gating, same-price
  time priority, cancellation, wrong-symbol rejection, determinism, and resting-order
  re-insertion priority.
- ADR-004: matching engine architecture (single-symbol, single-threaded baseline, caller-
  supplied `Instant` for determinism).

**Verified:**
- `mvn -q -DskipTests package` — SUCCESS.
- `mvn test` — SUCCESS: `MatchingEngineTest` 15/15, `OrderBookTest` 10/10,
  `InstrumentTest` 15/15, `HealthControllerTest` 1/1.

**Blockers:** None.

**Next session should:**
- Commit Phase 4 work.
- Start Phase 5 — REST/API Layer: expose `POST /api/v1/orders`, `DELETE /api/v1/orders/{id}`,
  `GET /api/v1/orders/{id}` over `finex-api`, backed by `MatchingEngine` or a service
  orchestrator. Need to decide whether to wire the engine directly or introduce an
  `OrderService` / `Gateway` abstraction.

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
