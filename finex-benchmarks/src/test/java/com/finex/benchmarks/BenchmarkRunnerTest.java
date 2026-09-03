package com.finex.benchmarks;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.results.RunResult;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkRunnerTest {

    @Test
    void benchmarksCanRunAndProduceResults() throws RunnerException {
        Options options = new OptionsBuilder()
                .include(MatchingEngineBenchmark.class.getSimpleName())
                .mode(Mode.Throughput)
                .timeUnit(TimeUnit.SECONDS)
                .warmupIterations(1)
                .measurementIterations(1)
                .measurementTime(TimeValue.milliseconds(200))
                .forks(1)
                .build();

        Collection<RunResult> results = new Runner(options).run();
        assertThat(results).isNotEmpty();
        for (RunResult result : results) {
            assertThat(result.getPrimaryResult().getScore()).isPositive();
        }
    }
}
