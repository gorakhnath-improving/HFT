package com.finex.benchmarks;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import com.finex.api.order.OrderService;
import com.finex.loadgenerator.LoadConfig;
import com.finex.loadgenerator.LoadGenerator;
import com.finex.loadgenerator.LoadResult;

/**
 * Multi-threaded end-to-end benchmark. Each thread gets a fresh {@link OrderService} per
 * invocation, so this measures aggregate single-symbol single-node throughput without
 * inter-thread contention on a shared book.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Threads(4)
public class MultiThreadedLoadGeneratorBenchmark {

    private LoadConfig config;

    @Setup
    public void setup() {
        config = LoadConfig.defaults();
    }

    @Benchmark
    public LoadResult runWorkload() {
        LoadGenerator generator = new LoadGenerator(new OrderService());
        return generator.run(config);
    }
}
