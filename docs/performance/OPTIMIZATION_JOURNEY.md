# The Optimization Journey

This is the narrative companion to `OPTIMIZATIONS.md` (per-optimization technical detail),
`OPTIMIZATION_PLAN.md` (living backlog and evidence levels), and `EXPERIMENTS.md` (raw
experiment logs). Read this first for the story; read those for the numbers and methodology.

The guiding rule throughout was: **measure → hypothesize → experiment → validate → document.**
Every optimization below started from a JFR or controlled-benchmark profile of the *current*
code, not from intuition about what "should" be slow in a trading system.

## Where we started

The Phase 1-24 baseline was written for correctness first: `BigDecimal` everywhere, plain
`ConcurrentHashMap`s, no latency measurement, no profiling. This was intentional — the Master
Plan's own principle is "Correctness → Tests → Observability → Benchmark → Profile → Optimize."
Optimizing an unverified engine is optimizing the wrong thing.

Baseline sustained throughput (`SustainedSharedServiceDriver`, 1.5M orders, 500 accounts,
single shared `OrderService`): **~77,389 ops/sec**.

## Low-risk wins from removing wasted work (OPT-002, OPT-003)

The first two optimizations weren't about faster algorithms — they were about *not doing work
that had no observable effect*:

- **OPT-002**: `OrderService` was building a full market-data snapshot (`BookUpdateFactory.aggregate`)
  on every order even when nobody was subscribed. Skipping it when there are no subscribers was
  the #1 CPU hotspot and #1 allocation source in the first JFR profile. **+24-28%.**
- **OPT-003**: `PortfolioService.markToMarket` re-scanned every account's position after every
  trade, even when the mark price hadn't changed since the last trade. A last-mark-price cache
  short-circuits the scan. **+39%.**

Lesson: the biggest early wins were deleting accidental work, not adding cleverness.

## Measuring the right thing (OPT-004)

Before going further, we added per-order latency percentile measurement (p50/p90/p99/p99.9/p99.99/max)
to the sustained driver. Throughput alone doesn't tell you whether an exchange is safe to run —
tail latency does. This wasn't a speedup; it was instrumentation that every subsequent
optimization's evidence depends on.

## The big allocation fix (OPT-005, OPT-006)

Profiling after OPT-003 pointed at `AccountRiskState.reservedCash()`/`reservedPosition()`,
which did a full stream/reduce over every open-order reservation on *every single risk
validation*. Maintaining running totals instead made this O(1). **+327%**, the single largest
win in the project. Then OPT-006 removed collection copies in `MatchingEngine`
(`List.copyOf`/`Map.copyOf` on every match) and reused a per-thread `ByteArrayOutputStream` in
`BinaryCodec`. **+14.2%** further, cumulative **+767%** vs. the original baseline
(671,089 ops/sec).

## A negative result, and why it matters (OPT-007)

OPT-007 removed further event-log allocation (an intermediate `Event` copy, a `ByteBuffer`
round-trip in `BinaryCodec`). The code is a real improvement in isolation — fewer allocations,
cleaner path — but a controlled 5-repetition A/B (same JDK, same JVM args, git-worktree-isolated
commits, interleaved runs) found the throughput delta was *smaller than the run-to-run standard
deviation on both commits*. We kept the code for its allocation-reduction value but explicitly
did **not** claim a validated speedup. This is the project's first documented negative result,
and it's exactly as valuable as a positive one: it stopped us from citing a number that wasn't real.

## The correctness gate before touching numerics (OPT-009)

The next hotspot was unambiguous: `BigDecimal.valueOf` and `Long.valueOf` boxing dominated
allocation across risk, matching, and settlement. But replacing decimal arithmetic in a
financial engine without a safety net is not an acceptable risk, full stop. So before touching
a single arithmetic operation, we built a randomized differential/financial-invariant stress
harness: a seeded command generator across seven workload profiles, canonical-state snapshots,
a first-divergence comparator, and five financial invariants (cash conservation, asset
conservation, ledger balance, order-quantity conservation, account isolation). It was validated
from 10 up to 1,000,000 generated commands.

Building this harness immediately found a real, pre-existing bug: `OrderService` appended a
`SUBMIT_ORDER` event *before* the risk check ran, so a rejected order was still in the event log,
and `ReplayEngine` had no way to catch the resulting exception — replaying any log with a
rejection silently dropped every later event. This had never been caught because the existing
hand-written replay test was four commands and never triggered a rejection. Fixed, with a
regression test that fails without the fix and passes with it.

## Fixed-point, only where it earns its keep (OPT-010)

With the correctness harness in place, we introduced a checked, scale-4, `long`-backed
fixed-point numeric type — not as a wholesale replacement of `BigDecimal`, but as a *selectable*
mode (`OrderService.NumericMode.FIXED_POINT`) applied only to the measured hot paths: risk
validation, reservations, and clearing. `BigDecimal` remains the default and the reference
implementation for correctness comparison. Every operation was defined with explicit overflow
and rounding semantics (checked arithmetic, `HALF_UP` where rounding is unavoidable, exact
`BigInteger` fallback for intermediate overflow) so that "fast" never meant "approximately
correct." The differential harness was extended to compare BigDecimal and fixed-point execution
byte-for-byte, including event-log payloads, and both replay and both invariant sets. A controlled
5-repetition A/B measured **+9.2%** mean throughput with improved median tail latency.

## A tested idea that didn't survive the A/B (OPT-011)

Post-OPT-010 profiling showed `ConcurrentHashMap` put/resize as the top combined CPU cost, and a
duplicate incoming-order cache write in `OrderService` was an obvious, low-risk target. Removing
it did shrink the local `putVal` profile share from 12.5% to 3.15%. But the controlled, isolated,
interleaved A/B measured a mean delta of only +3.47% against a baseline with 10.4% run-to-run
variance — paired deltas ranged from -9.7% to +19.5%. That's noise, not a signal. The change was
reverted; only the experiment record remains. This is the second documented negative result, and
it's the reason OPT-012 targeted something more specific.

## Precision data-structure work, twice (OPT-012, OPT-013)

The map cost identified in OPT-011's investigation was real — it just wasn't in the place OPT-011
tried to fix. `FixedPointAccountRiskState` is explicitly owner-serialized (single caller per
account by contract), so its three `ConcurrentHashMap<Long,Long>` reservation maps were paying
for concurrency and boxing they never used.

- **OPT-012** replaced those three maps with a purpose-built primitive open-address
  `long`-to-`long` map (linear probing, tombstone reuse, checked growth), scoped *only* to that
  owner-serialized state. Ten isolated interleaved repetitions measured **+5.09%** mean
  throughput; JFR showed boxed `Long` allocation pressure falling from 24.84% to 11.26%.
- **OPT-013** noticed that the new map's `put` call was now the single largest CPU frame, because
  cash, position, and reservation price were still three separate same-key lookups per order.
  Consolidating them into one key table with parallel value arrays cut that to one lookup per
  operation. Five valid isolated interleaved repetitions measured **+13.97%** mean throughput,
  with every latency percentile from p50 through p99.99 improving — at the cost of a small,
  explicitly documented regression in *maximum* latency and GC pause (71.2ms → 75.4ms max,
  127ms → 224ms max GC pause). We kept it, and we said so plainly rather than only reporting the
  numbers that looked good.

## Where this leaves the project

Every accepted optimization in this history has a paired positive-or-negative controlled
benchmark, not a single before/after run. Two proposed optimizations (OPT-007's throughput claim,
OPT-011 entirely) were explicitly rejected or downgraded by their own evidence. That discipline —
being willing to say "no measurable improvement" about your own work — is the actual point of
this exercise, more than any specific ops/sec number.

See `docs/FUTURE_RESEARCH.md` for what's left on the table, deliberately not started.
