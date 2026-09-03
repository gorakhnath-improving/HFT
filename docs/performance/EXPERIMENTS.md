# Performance Experiments

> Placeholder — recorded starting Phase 18/19. Each entry: Problem, Hypothesis, Baseline,
> Implementation, Benchmark, Result, Tradeoffs, Decision (Master Plan §35).

## OPT-011 — Eliminate redundant order-cache write

**Problem:** Post-OPT-010 JFR on 3,000,000 fixed-point orders attributes 12.5% of CPU samples
to `ConcurrentHashMap.putVal`, 6.4% to resize transfer, and 4.9% to `put`. Stack traces show
both `OrderService.processSubmitOrder`'s direct incoming-order `orderCache.put` and the
immediately following `result.updatedOrders()` loop writing that same incoming order. The loop
is still required for resting orders changed by a match.

**Evidence:** Current fixed-point baseline, five fresh JVMs, 1,500,000 orders/500 accounts,
`-Xms2g -Xmx2g`: 792,525 mean ops/s, 799,107 median, 20,690 stdev. JFR allocation pressure:
Long 24.84%, BigDecimal 24.08%, byte[] 10.11%, ConcurrentHashMap node 9.77%. No monitor-enter
or thread-park events; this is map CPU/allocation overhead, not lock contention.

**Hypothesis:** Removing the redundant direct cache write will eliminate one hash lookup/write
per accepted order, reduce `ConcurrentHashMap.putVal` CPU, and improve sustained throughput
without changing cache contents or externally observable state.

**Proposed change:** Remove only the direct `orderCache.put(orderId, result.order())`; retain the
`updatedOrders` loop as the single cache update path. Do not change map type or concurrency model.

**Correctness risks:** If any `MatchResult` path omits its incoming order, `getOrder` could lose
that order. Existing matching code inserts the final incoming order on every return path; add an
explicit regression test before relying on this invariant. Full tests, randomized differential,
replay, and financial invariants remain mandatory.

**Benchmark:** Controlled interleaved A/B against commit `7a3037d`, same JDK/JVM, fixed-point
mode, 1,500,000 orders, 500 accounts, at least five repetitions per variant. Compare throughput,
latency distribution, allocation, GC, and post-change JFR.

**Acceptance criteria:** Keep only if the throughput/latency benefit exceeds observed variance
without correctness regression and the targeted cache-write hotspot decreases. Otherwise revert
and record OPT-011 as rejected.

**Implementation:** Removed the direct incoming-order cache write and added a temporary regression
assertion that every matching return path includes the final incoming order in `updatedOrders`.
Targeted matching, API replay/fixed-point, and differential stress tests passed.

**Controlled result:** Five interleaved baseline/candidate repetitions from isolated builds:

| Variant | Mean ops/s | Median ops/s | Stdev |
|---|---:|---:|---:|
| Baseline (`7a3037d`) | 784,735 | 780,105 | 81,539 |
| Candidate | 811,992 | 796,472 | 38,262 |

Mean delta was +3.47%, but baseline variation was 10.4% and paired deltas were +10.1%, −6.6%,
+19.5%, −9.7%, and +8.9%. Latency likewise had no consistent directional improvement.
The result is inside environmental noise and is not a validated speedup.

**Post-change profile:** `ConcurrentHashMap.putVal` fell from 12.5% to 3.15% of CPU samples,
confirming the local write was removed. However, `ConcurrentHashMap.transfer` remained 7.09%,
`Long.equals` appeared at 9.06%, and sampled allocation attribution shifted without a reliable
end-to-end allocation reduction. Candidate JFR throughput was 776,808 ops/s versus baseline
742,260, but these are single profiled runs and not decision evidence.

**Decision:** **REJECTED / REVERTED.** The production change and temporary regression assertion
were removed. The local operation reduction is real, but its end-to-end effect is not separable
from benchmark noise. Next evidence-based candidate: address growing `ConcurrentHashMap` resize
transfer or boxed key/value traffic with a separately selectable, correctness-tested data-layout
experiment rather than replacing concurrency semantics casually.

## OPT-012 — Primitive fixed-point reservation maps

**Problem/evidence:** The post-OPT-010 baseline attributes 24.84% of allocation pressure to
boxed `Long`, 9.77% to `ConcurrentHashMap.Node`, and substantial CPU to map put/resize. JFR
stacks identify `FixedPointAccountRiskState.reserveOrder` as a major source. This state is
owner-serialized by contract, so concurrent maps inside it provide no valid synchronization.

**Hypothesis:** Replacing only the three fixed-point reservation `Map<Long,Long>` instances with
a primitive long-to-long open-address structure will remove key/value boxing and node allocation,
reduce resize/pointer-chasing CPU, and improve throughput without changing service-level maps or
concurrency semantics.

**Baseline:** Reuse the current isolated OPT-011 baseline: 784,735 mean ops/s, 780,105 median,
81,539 stdev over five interleaved 1.5M-order fixed-point runs; baseline JFR allocation pressure
Long 24.84%, ConcurrentHashMap node 9.77%.

**Proposed change:** Add a package-private primitive map supporting positive order IDs, put,
remove, and get-or-default with checked growth; use it only in `FixedPointAccountRiskState`.

**Correctness risks:** Tombstone/probe errors, lost previous values, incorrect reservation totals,
and resize corruption. Add collision, removal, replacement, resize, and randomized reference-map
tests, then run full differential/replay/invariant gates.

**Acceptance criteria:** At least five isolated interleaved A/B repetitions. Keep only if benefit
exceeds observed variation and JFR shows boxed Long/ConcurrentHashMap-node pressure falling without
new correctness, GC, or tail-latency regressions.

**Implementation:** Added package-private `LongLongHashMap`, an open-address map for positive order
IDs with primitive key/value arrays, linear probing, tombstone reuse, and checked growth. Replaced
only the three maps inside `FixedPointAccountRiskState`; default BigDecimal and service-level
concurrent maps are unchanged.

**Correctness:** Map unit tests cover replacement/removal/default semantics, 10k-key resize and
tombstone reuse, invalid keys, and 100k randomized operations against `HashMap`. Full `mvn test`
passed across 16 modules. All seven stress profiles passed exact BigDecimal-vs-fixed differential,
both-mode replay, and both invariant suites at 100k commands (seed 1).

**Controlled result:** Ten isolated interleaved 1.5M-order pairs (first five baseline-first, second
five candidate-first):

| Variant | Mean ops/s | Median ops/s | Stdev |
|---|---:|---:|---:|
| Baseline | 758,374 | 754,974 | 39,091 |
| Primitive map | 796,988 | 788,250 | 31,639 |

Mean throughput delta: **+5.09%**; median delta: **+4.41%**. Paired mean was +5.38% with
2.36 percentage-point standard error; 8 of 10 pairs favored the candidate. Median latency
baseline/candidate: p50 875/875 ns, p90 1,750/1,605 ns, p99 4,792/4,604 ns, p99.9
21,063/16,459 ns, p99.99 44,730/42,792 ns, max 74.3/70.8 ms.

**Post-change JFR:** Boxed Long allocation pressure fell 24.84%→11.26%; ConcurrentHashMap node
pressure fell 9.77%→7.18% (remaining service maps); young GC 18→16; total GC pause 982→747 ms.
`LongLongHashMap.put` became the largest CPU frame at 16.33%, replacing the combined boxed-map
put/transfer cost rather than eliminating map work. No monitor-enter or thread-park events.

**Decision:** **VALIDATED / KEPT.** Improvement is specific to the owner-serialized fixed-point
risk state and tested workload; it is not evidence to replace service-level concurrent maps.
