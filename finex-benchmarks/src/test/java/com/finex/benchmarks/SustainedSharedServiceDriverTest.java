package com.finex.benchmarks;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SustainedSharedServiceDriverTest {

    @Test
    void runsDeterministicallyAndProducesExpectedTradeCount() {
        // Small scale for fast unit-test execution; the same code path is used for the
        // large 1.5M-order OPT-002 evidence run (see docs/performance/OPTIMIZATIONS.md).
        SustainedSharedServiceDriver.Result result = SustainedSharedServiceDriver.run(2_000, 50);

        assertThat(result.submitted()).isEqualTo(2_000);
        // Sides flip every `accountCount` orders and each flip crosses the whole book,
        // so exactly half of all orders after the first block should trade.
        assertThat(result.trades()).isEqualTo(1_000);
        assertThat(result.throughputOpsPerSec()).isPositive();
    }
}
