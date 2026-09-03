package com.finex.benchmarks;

import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

/**
 * Entry point for running all JMH benchmarks in this module.
 */
public class BenchmarkRunner {

    public static void main(String[] args) throws RunnerException {
        new Runner(options()).run();
    }

    static Options options() {
        return new OptionsBuilder()
                .include("com.finex.benchmarks")
                .mode(Mode.Throughput)
                .timeUnit(java.util.concurrent.TimeUnit.SECONDS)
                .warmupIterations(2)
                .warmupTime(TimeValue.seconds(2))
                .measurementIterations(3)
                .measurementTime(TimeValue.seconds(1))
                .forks(0) // same-JVM for easy Maven exec:java; use -f 1 for serious runs
                .build();
    }
}
