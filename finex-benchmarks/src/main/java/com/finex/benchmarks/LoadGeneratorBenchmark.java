package com.finex.benchmarks;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;

import com.finex.api.order.OrderService;
import com.finex.loadgenerator.LoadConfig;
import com.finex.loadgenerator.LoadGenerator;
import com.finex.loadgenerator.LoadResult;

/**
 * End-to-end benchmark running the full {@link LoadGenerator} workload. A fresh
 * {@link OrderService} is used each invocation to keep the benchmark self-contained.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class LoadGeneratorBenchmark {

    private final LoadConfig config = new LoadConfig(
            List.of("BTC-USD"),
            List.of(100L, 200L),
            20,
            new BigDecimal("50000"),
            new BigDecimal("0.01"),
            new BigDecimal("100"),
            1L,
            Instant.parse("2026-01-01T00:00:00Z"));

    @Benchmark
    public LoadResult runWorkload() {
        LoadGenerator generator = new LoadGenerator(new OrderService());
        return generator.run(config);
    }
}
