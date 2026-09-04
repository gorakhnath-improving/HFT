package com.finex.benchmarks;

import java.nio.file.Files;
import java.nio.file.Path;

import com.finex.api.order.OrderService;
import com.finex.loadgenerator.LoadConfig;
import com.finex.loadgenerator.LoadGenerator;
import com.finex.loadgenerator.LoadResult;

import jdk.jfr.Recording;

/**
 * Runs a {@link LoadGenerator} workload while capturing a JDK Flight Recorder (JFR) recording.
 * Useful for Phase 18 profiling work.
 */
public class ProfileRunner {

    public record ProfileResult(Path jfrFile, LoadResult loadResult) {
    }

    public static void main(String[] args) throws Exception {
        Path output = args.length > 0 ? Path.of(args[0]) : Files.createTempFile("finex", ".jfr");
        LoadConfig config = LoadConfig.defaults();
        ProfileResult result = run(config, output);
        System.out.println("JFR recording written to: " + result.jfrFile().toAbsolutePath());
        System.out.println("Submitted: " + result.loadResult().submitted());
        System.out.println("Trades: " + result.loadResult().trades());
        System.out.println("Throughput: " + result.loadResult().throughput() + " ops/s");
    }

    /**
     * Runs the configured workload under a JFR recording and writes it to {@code output}.
     */
    public static ProfileResult run(LoadConfig config, Path output) throws Exception {
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        LoadGenerator generator = new LoadGenerator(new OrderService());
        try (Recording recording = new Recording()) {
            recording.setName("FinEx Profile");
            recording.start();
            LoadResult result = generator.run(config);
            recording.stop();
            recording.dump(output);
            return new ProfileResult(output, result);
        }
    }
}
