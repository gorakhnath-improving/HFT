package com.finex.loadgenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Summary of a load run.
 */
public record LoadResult(
        int submitted,
        int trades,
        long elapsedNanos,
        double throughput,
        double averageLatencyNanos,
        double maxLatencyNanos) {

    public LoadResult {
        if (submitted < 0) {
            throw new IllegalArgumentException("submitted must not be negative");
        }
        if (trades < 0) {
            throw new IllegalArgumentException("trades must not be negative");
        }
        if (elapsedNanos < 0) {
            throw new IllegalArgumentException("elapsedNanos must not be negative");
        }
    }

    public BigDecimal throughputRounded() {
        return BigDecimal.valueOf(throughput).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal averageLatencyMicros() {
        return BigDecimal.valueOf(averageLatencyNanos / 1000.0).setScale(2, RoundingMode.HALF_UP);
    }
}
