package com.finex.benchmarks;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import com.finex.common.domain.Order;
import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;
import com.finex.matching.MatchResult;
import com.finex.matching.MatchingEngine;

/**
 * JMH microbenchmark for {@link MatchingEngine} full matching cycles.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class MatchingEngineBenchmark {

    private MatchingEngine engine;
    private long sequence;
    private BigDecimal price;

    @Setup
    public void setup() {
        engine = new MatchingEngine("BTC-USD");
        sequence = 0;
        price = new BigDecimal("50000");
    }

    @Benchmark
    public MatchResult placeBuyAndSell() {
        sequence++;
        Order sell = new Order(
                sequence,
                "cid-" + sequence,
                100L,
                "BTC-USD",
                Side.SELL,
                OrderType.LIMIT,
                price,
                BigDecimal.ONE,
                BigDecimal.ONE,
                sequence,
                Instant.now(),
                OrderStatus.OPEN);
        engine.placeOrder(sell, Instant.now());

        sequence++;
        Order buy = new Order(
                sequence,
                "cid-" + sequence,
                200L,
                "BTC-USD",
                Side.BUY,
                OrderType.LIMIT,
                price,
                BigDecimal.ONE,
                BigDecimal.ONE,
                sequence,
                Instant.now(),
                OrderStatus.OPEN);
        return engine.placeOrder(buy, Instant.now());
    }
}
