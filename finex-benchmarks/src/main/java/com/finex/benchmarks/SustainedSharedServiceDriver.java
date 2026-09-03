package com.finex.benchmarks;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

import com.finex.api.order.OrderRequest;
import com.finex.api.order.OrderService;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.matching.MatchResult;

/**
 * Reproducible, deterministic, long-running driver against a single shared
 * {@link OrderService}, used as evidence for OPT-002/OPT-003/OPT-004 (see
 * {@code docs/performance/OPTIMIZATIONS.md} and {@code OPTIMIZATION_EVIDENCE.md}).
 *
 * <p>Unlike the short JMH micro-benchmarks in this module, this driver runs a large,
 * fixed number of orders across many accounts in a single sustained run, which gives much
 * lower relative variance and is suitable for profiling with JFR
 * ({@code -XX:StartFlightRecording=filename=out.jfr,settings=profile}).</p>
 *
 * <p>Sides alternate in blocks of {@code accountCount} orders so each account's cash and
 * position stay bounded over an arbitrarily long run (unlike a naive per-order alternation,
 * which would drain some accounts' cash after a few thousand orders).</p>
 */
public final class SustainedSharedServiceDriver {

    private SustainedSharedServiceDriver() {
    }

    public static void main(String[] args) {
        int totalOrders = args.length > 0 ? Integer.parseInt(args[0]) : 1_500_000;
        int accountCount = args.length > 1 ? Integer.parseInt(args[1]) : 500;
        Result result = run(totalOrders, accountCount);
        System.out.println("orders=" + result.submitted()
                + " trades=" + result.trades()
                + " elapsedMs=" + (result.elapsedNanos() / 1_000_000)
                + " throughputOpsPerSec=" + result.throughputOpsPerSec()
                + " latencyNs=" + result.latencySummary());
    }

    public record Result(int submitted, int trades, long elapsedNanos, double throughputOpsPerSec,
            LatencySummary latencySummary) {
    }

    public record LatencySummary(long p50, long p90, long p99, long p999, long p9999, long max) {
        @Override
        public String toString() {
            return String.format(Locale.US,
                    "p50=%d p90=%d p99=%d p99.9=%d p99.99=%d max=%d", p50, p90, p99, p999, p9999, max);
        }
    }

    /**
     * Runs the workload against a fresh {@link OrderService} and returns the outcome.
     */
    public static Result run(int totalOrders, int accountCount) {
        OrderService service = new OrderService();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        int trades = 0;

        long[] latencies = new long[totalOrders];
        long start = System.nanoTime();
        for (int i = 0; i < totalOrders; i++) {
            long orderStart = System.nanoTime();
            t = t.plus(Duration.ofMillis(50));
            // Flip side every `accountCount` orders so each account alternates roughly
            // evenly between selling and buying, keeping cash/position bounded.
            boolean sell = ((i / accountCount) % 2 == 0);
            long account = 1000L + (i % accountCount);
            BigDecimal price = sell ? new BigDecimal("49990") : new BigDecimal("50010");
            OrderRequest request = new OrderRequest(
                    "cid-" + i, "BTC-USD", sell ? Side.SELL : Side.BUY, OrderType.LIMIT,
                    price, new BigDecimal("0.01"), account);
            MatchResult result = service.submitOrder(request, t);
            latencies[i] = System.nanoTime() - orderStart;
            trades += result.trades().size();
        }
        long elapsed = System.nanoTime() - start;
        double throughput = totalOrders / (elapsed / 1_000_000_000.0);
        return new Result(totalOrders, trades, elapsed, throughput, summarize(latencies));
    }

    static LatencySummary summarize(long[] latencies) {
        if (latencies.length == 0) {
            return new LatencySummary(0, 0, 0, 0, 0, 0);
        }
        long[] sorted = latencies.clone();
        Arrays.sort(sorted);
        return new LatencySummary(
                percentile(sorted, 0.50),
                percentile(sorted, 0.90),
                percentile(sorted, 0.99),
                percentile(sorted, 0.999),
                percentile(sorted, 0.9999),
                sorted[sorted.length - 1]);
    }

    private static long percentile(long[] sorted, double fraction) {
        int index = (int) Math.ceil(fraction * sorted.length) - 1;
        if (index < 0) {
            index = 0;
        } else if (index >= sorted.length) {
            index = sorted.length - 1;
        }
        return sorted[index];
    }
}
