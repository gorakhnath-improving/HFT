package com.finex.benchmarks.stress;

import java.util.List;
import java.util.Optional;

/**
 * Outcome of one {@link StressHarness#run} invocation. Carries everything needed to
 * reproduce a failure ({@code seed}, {@code profile}, {@code commandCount}) alongside the
 * result of each of the three checks.
 */
public record StressHarnessResult(
        long seed,
        WorkloadProfile profile,
        int commandCount,
        long elapsedMs,
        Optional<String> determinismMismatch,
        Optional<String> replayMismatch,
        Optional<String> numericMismatch,
        Optional<String> fixedPointReplayMismatch,
        List<String> invariantViolations,
        List<String> fixedPointInvariantViolations,
        int rejectedCount,
        int cancelAttempts,
        int cancelSuccesses) {

    public boolean passed() {
        return determinismMismatch.isEmpty() && replayMismatch.isEmpty()
                && numericMismatch.isEmpty() && fixedPointReplayMismatch.isEmpty()
                && invariantViolations.isEmpty() && fixedPointInvariantViolations.isEmpty();
    }

    /**
     * A human-readable, reproducible failure report. Deliberately does not dump full
     * snapshot state; {@link DifferentialComparator} already reduced any mismatch to its
     * first divergence.
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("seed=").append(seed)
                .append(" profile=").append(profile)
                .append(" commandCount=").append(commandCount)
                .append(" elapsedMs=").append(elapsedMs)
                .append(" rejected=").append(rejectedCount)
                .append(" cancelAttempts=").append(cancelAttempts)
                .append(" cancelSuccesses=").append(cancelSuccesses)
                .append(" passed=").append(passed());
        determinismMismatch.ifPresent(m -> sb.append("\nDETERMINISM FAILURE: ").append(m));
        replayMismatch.ifPresent(m -> sb.append("\nREPLAY FAILURE: ").append(m));
        numericMismatch.ifPresent(m -> sb.append("\nFIXED-POINT DIFFERENTIAL FAILURE: ").append(m));
        fixedPointReplayMismatch.ifPresent(m -> sb.append("\nFIXED-POINT REPLAY FAILURE: ").append(m));
        if (!invariantViolations.isEmpty()) {
            sb.append("\nINVARIANT FAILURES:");
            for (String violation : invariantViolations) {
                sb.append("\n  - ").append(violation);
            }
        }
        if (!fixedPointInvariantViolations.isEmpty()) {
            sb.append("\nFIXED-POINT INVARIANT FAILURES:");
            for (String violation : fixedPointInvariantViolations) {
                sb.append("\n  - ").append(violation);
            }
        }
        return sb.toString();
    }
}
