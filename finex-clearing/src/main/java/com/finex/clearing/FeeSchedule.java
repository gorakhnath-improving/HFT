package com.finex.clearing;

import java.math.BigDecimal;

/**
 * Immutable fee schedule. Rates are expressed as decimals (e.g. 0.001 = 0.1%).
 */
public record FeeSchedule(BigDecimal takerRate, BigDecimal makerRate) {

    public FeeSchedule {
        if (takerRate == null || takerRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("takerRate must not be negative");
        }
        if (makerRate == null) {
            throw new IllegalArgumentException("makerRate must not be null");
        }
    }

    public static FeeSchedule defaults() {
        return new FeeSchedule(new BigDecimal("0.001"), BigDecimal.ZERO);
    }
}
