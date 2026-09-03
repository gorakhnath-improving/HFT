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
