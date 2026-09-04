package com.finex.benchmarks.stress;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OPT-009 fast correctness gate: small/medium deterministic randomized scenarios across
 * every {@link WorkloadProfile}, run as part of {@code mvn test}. These are intentionally
 * small enough to stay fast; {@code com.finex.benchmarks.stress.StressDriver} (a
 * {@code main} class, following the same convention as
 * {@code SustainedSharedServiceDriver}) is used for the 100k/1M-scale runs, invoked
 * manually via {@code java -cp}, exactly like the other long-running benchmark drivers in
 * this module.
 */
class StressHarnessTest {

    private static final long[] SEEDS = {1L, 42L, 12345L};

    @ParameterizedTest
    @EnumSource(WorkloadProfile.class)
    void tinyScenarioIsDeterministicReplayableAndInvariantSafe(WorkloadProfile profile) {
        for (long seed : SEEDS) {
            StressHarnessResult result = StressHarness.run(seed, profile, 10);
            assertThat(result.passed()).as(result::describe).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(WorkloadProfile.class)
    void smallScenarioIsDeterministicReplayableAndInvariantSafe(WorkloadProfile profile) {
        for (long seed : SEEDS) {
            StressHarnessResult result = StressHarness.run(seed, profile, 1_000);
            assertThat(result.passed()).as(result::describe).isTrue();
        }
    }

    @Test
    void mediumBalancedScenarioIsDeterministicReplayableAndInvariantSafe() {
        // A single larger run (not the full profile x seed matrix) keeps mvn test fast
        // while still exercising a scenario large enough to produce many trades, partial
        // fills, cancels of already-filled orders, and rate-limit rejections in the same
        // run.
        StressHarnessResult result = StressHarness.run(2026L, WorkloadProfile.BALANCED, 10_000);
        assertThat(result.passed()).as(result::describe).isTrue();
        assertThat(result.cancelAttempts()).isGreaterThan(0);
    }

    @Test
    void reproducingTheSameSeedProfileAndCountAlwaysGeneratesTheSameCommands() {
        var first = CommandGenerator.generate(999L, WorkloadProfile.MATCH_HEAVY, 500);
        var second = CommandGenerator.generate(999L, WorkloadProfile.MATCH_HEAVY, 500);
        assertThat(first).isEqualTo(second);
    }
}
