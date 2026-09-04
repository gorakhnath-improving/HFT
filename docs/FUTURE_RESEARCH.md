# Future Research

This project has reached a deliberate stopping point (see `AGENT_CONTEXT.md` and
`PROGRESS.md`: **FINALIZED / PORTFOLIO COMPLETE**). The items below are **not** a current
TODO list and are **not** authorized work. They are evidence-informed directions a future
session could investigate, each with the current evidence, the risk, and why it wasn't
pursued now. Nothing here should be started without a fresh profile confirming it's still
the top-ranked opportunity, and without the same measure → hypothesize → experiment →
validate → document discipline used for OPT-001 through OPT-013.

## 1. Protocol / event byte-array allocation

**Evidence:** Post-OPT-013 JFR shows `byte[]` allocation pressure and `BinaryCodec.encodePayload`
/ `encodeToBytes` as now-prominent frames, alongside `ByteArrayOutputStream.ensureCapacity` and
`DecimalDigits.uncheckedGetCharsLatin1` (decimal-to-string conversion for the wire format).

**Why interesting:** With the risk/reservation hot path optimized, this is the next largest
allocation source specific to the order-submission path rather than a general JVM cost.

**Risk:** Medium. Any change to `BinaryCodec` or `CommandSerializer` risks changing the on-wire
event format, which would break replay compatibility with previously recorded event logs. Must
preserve byte-identical output for existing message types, or introduce and document an explicit
version/migration story.

**Why not now:** Not yet profiled in isolation against a fresh controlled baseline; the exact
allocation source (decimal string conversion vs. buffer growth vs. frame header writes) hasn't
been attributed precisely enough to propose a minimal experiment.

## 2. Ledger-entry allocation

**Evidence:** `LedgerEntry.<init>` and `InMemoryLedger.post` remain consistently visible in every
JFR recording taken during OPT-012/OPT-013, at roughly 6-13% allocation/CPU share depending on
the run.

**Why interesting:** Every trade posts multiple `LedgerEntry` records (cash legs, asset legs,
fee accrual); this is proportional to trade volume, not order volume, so it may matter more as
match rate increases.

**Risk:** Medium-high. The ledger is the audit trail; any change must preserve exact double-entry
balance validation and must not change what is persisted or how `balance()` is computed.

**Why not now:** No specific optimization hypothesis has been profiled to the point of a minimal,
testable change (e.g. "record pooling" or "narrower entry representation" are both plausible
but unvalidated ideas, not measured findings).

## 3. GC / tail-latency investigation

**Evidence:** OPT-013 measurably improved p50-p99.99 latency but *regressed* maximum latency
(71.2ms → 75.4ms) and maximum GC pause (127ms → 224ms) in the paired controlled benchmark. This
is documented, not hidden, in `OPTIMIZATIONS.md` and `EXPERIMENTS.md`.

**Why interesting:** For an exchange, worst-case tail latency arguably matters more than median
throughput. The current benchmark methodology (5-10 fresh-JVM repetitions on a shared
development machine) is good enough to validate percentile-level improvements but is not
rigorous enough to explain a single-run GC pause regression — that requires dedicated GC-log
analysis (`-Xlog:gc*`) across many more repetitions on an isolated machine.

**Risk:** Low to investigate (profiling only), potentially high to fix (may require heap sizing,
GC algorithm choice, or object-lifetime changes).

**Why not now:** Requires a controlled, non-shared benchmarking environment to be conclusive;
the current shared development machine has already been shown (during the OPT-007 session) to
introduce load-dependent variance of 2-3x, which would make any GC-tuning conclusion unreliable.

## 4. Matching-engine structural optimization

**Evidence:** `MatchingEngine.placeOrder` is consistently one of the top 1-2 CPU frames across
every recent JFR recording (7-11% depending on the run), and is now proportionally larger since
OPT-012/OPT-013 reduced risk-side cost.

**Why interesting:** This is the actual matching hot path — price-time-priority order-book
traversal. `OrderBook` currently uses `TreeMap` for price levels; a specialized structure
(e.g. sorted primitive arrays or an intrusive price-level list) could reduce navigation cost.

**Risk:** High. This is core matching-correctness logic. Any change must preserve exact
price-time priority and pass the full differential/replay/invariant suite at every scale
already validated (10 through 1,000,000 commands), not just unit tests.

**Why not now:** This is exactly the kind of "large architectural rewrite whose cost may exceed
the value at this stage" that `OPTIMIZATION_PLAN.md`'s own stopping condition calls out. It
deserves a dedicated session with its own experiment design, not a quick patch appended to the
current work.

## 5. Remaining BigDecimal boundaries

**Evidence:** Matching, order book, portfolio, ledger, protocol, and the REST API surface all
still use `BigDecimal`, by design (see `OPTIMIZATIONS.md` OPT-010: "this intentionally limits
conversion to measured hot paths rather than rewriting cold boundaries").

**Why interesting:** If a future profile shows one of these boundaries (e.g. portfolio
mark-to-market, which still uses scale-20 `BigDecimal` division) as a genuine hot path under a
specific workload, the same fixed-point primitive (`FixedPoint`) already exists and is tested.

**Risk:** Varies by boundary. Portfolio P&L and ledger balance-checking are both
correctness-sensitive; conversion would require the same differential-equivalence rigor as
OPT-010.

**Why not now:** No current profile shows any of these boundaries as a top hotspot. Converting
them "for consistency" without evidence is explicitly against this project's methodology.

## 6. Concurrency contract refinement / service-level maps

**Evidence:** `OrderService`, `PortfolioService`, `EngineShard`, and `ApiKeyService` all use
`ConcurrentHashMap` for account/order/symbol lookups. OPT-012 deliberately did *not* touch these,
because they don't have the same owner-serialized contract that justified replacing
`FixedPointAccountRiskState`'s internal maps.

**Why interesting:** If a future concurrency model (e.g. genuinely parallel per-shard processing)
is introduced, these maps' actual access patterns would need to be re-examined.

**Risk:** High. Changing map semantics here touches real concurrent access from multiple callers
(REST threads), unlike the single-writer fixed-point risk state.

**Why not now:** No evidence these maps are a bottleneck under the current single-threaded
sustained-driver benchmark, and no concurrency-model change is currently planned.

## 7. Single-writer / sharded architecture

**Evidence:** `ShardCoordinator`/`EngineShard` already exist and route symbols to shards, but the
current benchmarks all exercise a single shard; there is no measured multi-shard throughput data.

**Why interesting:** This is the largest remaining lever toward significantly higher throughput —
running independent symbols on independent threads/cores with no shared mutable state, in the
spirit of a disruptor-style single-writer design.

**Risk:** Highest in this list. This is a genuine architectural change: thread ownership,
inter-shard event ordering, replay semantics across shards, and the differential/invariant
harness would all need rethinking for a multi-threaded model.

**Why not now:** This is the "large architectural rewrite" the plan's stopping condition
anticipates. It should be scoped as its own project phase with its own success criteria, not
appended incrementally to the current single-threaded optimization sequence.

## 8. Metrics batching (OPT-008, still deferred)

**Evidence:** `MetricsService`/Micrometer calls have not appeared in the top CPU or allocation
frames in any JFR profile taken across this entire project, from the original post-OPT-007
profile through the latest post-OPT-013 profile.

**Why interesting:** It's a common HFT-systems optimization pattern in general, which is
precisely why it's worth explicitly *not* doing here without evidence.

**Risk:** Medium if attempted — batching or async metrics recording changes observability
guarantees (e.g. metrics could lag or be lost on crash).

**Why not now:** No profile has ever justified it. This is the project's clearest example of
resisting a "sounds right" optimization that the evidence has never supported. Revisit only if
a future profile changes this.
