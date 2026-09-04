package com.finex.benchmarks.stress;

/**
 * A named, documented distribution of generated commands for {@link CommandGenerator}.
 *
 * <p>These distributions are illustrative, not calibrated to any real market. The only
 * property that matters for OPT-009 is that they are deterministic and reproducible given
 * the same seed (Master Plan performance-engineering §OPT-009).
 *
 * @param cancelRatio        fraction of generated commands (after the first submit) that are
 *                           cancels of a previously submitted order, in [0, 1)
 * @param crossingBias       -1.0..1.0; positive values bias BUY prices above the resting mid
 *                           (more crossing / more trades), negative values bias BUY prices
 *                           below the resting mid (orders rest instead of matching)
 * @param accountCount       number of distinct accounts to generate orders for
 * @param symbolCount        number of distinct symbols to generate orders for
 */
public enum WorkloadProfile {
    BALANCED(0.30, 0.0, 20, 3),
    MATCH_HEAVY(0.05, 0.9, 20, 2),
    CANCEL_HEAVY(0.60, 0.0, 20, 2),
    RESTING_BOOK(0.05, -0.9, 20, 2),
    CROSSING(0.10, 0.95, 10, 1),
    MULTI_ACCOUNT(0.20, 0.1, 200, 1),
    MULTI_INSTRUMENT(0.20, 0.1, 20, 20);

    private final double cancelRatio;
    private final double crossingBias;
    private final int accountCount;
    private final int symbolCount;

    WorkloadProfile(double cancelRatio, double crossingBias, int accountCount, int symbolCount) {
        this.cancelRatio = cancelRatio;
        this.crossingBias = crossingBias;
        this.accountCount = accountCount;
        this.symbolCount = symbolCount;
    }

    public double cancelRatio() {
        return cancelRatio;
    }

    public double crossingBias() {
        return crossingBias;
    }

    public int accountCount() {
        return accountCount;
    }

    public int symbolCount() {
        return symbolCount;
    }
}
