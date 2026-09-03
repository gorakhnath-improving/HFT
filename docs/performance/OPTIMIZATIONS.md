# Optimizations

> Placeholder — recorded starting Phase 19, including failed optimizations with useful
> engineering insight (Master Plan §35).

## Phase 19 — First optimization: in-place resting-order updates

### Hypothesis

`MatchingEngine` was removing and re-adding a partially filled resting order on every fill.
For a single price level this means a `TreeMap` remove/insert plus an `ArrayList` remove and
sorted insertion. Replacing the order reference in-place should reduce that work.

### Change

- Added `OrderBook.replaceOrder(long orderId, Order newOrder)` to update a partially filled
  order without a TreeMap remove/insert.
- Updated `MatchingEngine` to call `replaceOrder` when the resting order still has remaining
  quantity, and `cancelOrder` only when it is fully filled.

### Results

Measured with `finex-benchmarks` `BenchmarkRunner` (single-JVM, no fork):

| Benchmark | Before (ops/s) | After (ops/s) | Delta |
|-----------|---------------:|--------------:|------:|
| `MatchingEngineBenchmark.placeBuyAndSell` | 3,655,769.3 | 3,713,991.7 | +1.6% |
| `OrderBookBenchmark.addAndCancel` | 15,067,286.9 | 14,607,902.8 | -3.0% (noise) |
| `OrderServiceBenchmark.submitLimitOrder` | 55,762.9 | 58,178.3 | +4.3% |

### Analysis

- The `MatchingEngine` improvement is small and within normal JMH variance on this machine.
- The dominant cost is still `BigDecimal` allocation and `TreeMap` navigation, not the
  remove/re-insert of the price level.
- This validates ADR-003: the TreeMap baseline is acceptable for correctness, but a 1M
  orders/sec target will require replacing `BigDecimal`/`TreeMap` with fixed-point primitive
  structures later.

### Conclusion

The in-place update is a correctness-preserving cleanup and removes unnecessary work, so it
is kept. It is not a breakthrough optimization. The next evidence-driven optimization cycle
(Phase 24) should benchmark alternative book structures (`Long2ObjectOpenHashMap`, sorted
arrays, intrusive lists) and fixed-point price representation.
