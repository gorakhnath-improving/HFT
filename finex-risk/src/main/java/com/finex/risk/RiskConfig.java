package com.finex.risk;

import java.math.BigDecimal;

/**
 * Static per-account risk limits used by {@link RiskEngine}.
 */
public record RiskConfig(
        BigDecimal maxOrderQuantity,
        BigDecimal maxOrderNotional,
        BigDecimal maxPosition,
        BigDecimal maxCashExposure,
        BigDecimal priceCollarFraction,
        int maxOrdersPerSecond) {

    public RiskConfig {
        if (maxOrderQuantity == null || maxOrderQuantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maxOrderQuantity must be positive");
        }
        if (maxOrderNotional == null || maxOrderNotional.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maxOrderNotional must be positive");
        }
        if (maxPosition == null || maxPosition.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maxPosition must be positive");
        }
        if (maxCashExposure == null || maxCashExposure.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maxCashExposure must be positive");
        }
        if (priceCollarFraction == null || priceCollarFraction.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("priceCollarFraction must not be negative");
        }
        if (maxOrdersPerSecond <= 0) {
            throw new IllegalArgumentException("maxOrdersPerSecond must be positive");
        }
    }

    /**
     * A conservative default config suitable for the baseline.
     */
    public static RiskConfig defaults() {
        return new RiskConfig(
                new BigDecimal("1000"),
                new BigDecimal("500000"),
                new BigDecimal("100"),
                new BigDecimal("500000"),
                new BigDecimal("0.10"),
                10);
    }
}
