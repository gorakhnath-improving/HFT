package com.finex.benchmarks;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import com.finex.api.order.OrderRequest;
import com.finex.api.order.OrderService;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.matching.MatchResult;

/**
 * Component benchmark for {@link OrderService} end-to-end submit flow.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class OrderServiceBenchmark {

    private OrderService service;
    private long sequence;
    private Instant timestamp;

    @Setup
    public void setup() {
        service = new OrderService();
        sequence = 0;
        timestamp = Instant.parse("2026-01-01T00:00:00Z");
    }

    @Benchmark
    public MatchResult submitLimitOrder() {
        sequence++;
        timestamp = timestamp.plus(Duration.ofMillis(200));

        // Work in blocks of 100: first block sells, second block buys, repeating.
        // This keeps each account's cash and position roughly flat across the benchmark.
        boolean isSell = (((sequence - 1) / 100) % 2) == 0;
        Side side = isSell ? Side.SELL : Side.BUY;
        BigDecimal price = isSell ? new BigDecimal("49990") : new BigDecimal("50010");
        long account = 100L + (sequence % 100);

        OrderRequest request = new OrderRequest(
                "cid-" + sequence,
                "BTC-USD",
                side,
                OrderType.LIMIT,
                price,
                new BigDecimal("0.01"),
                account);
        return service.submitOrder(request, timestamp);
    }
}
