package com.finex.portfolio;

import java.math.BigDecimal;
import java.util.List;

/**
 * Immutable snapshot of an account's portfolio.
 */
public record Portfolio(
        long accountId,
        BigDecimal cash,
        List<Position> positions,
        BigDecimal totalEquity) {

    public Portfolio {
        if (cash == null) {
            throw new IllegalArgumentException("cash must not be null");
        }
        if (totalEquity == null) {
            throw new IllegalArgumentException("totalEquity must not be null");
        }
        positions = List.copyOf(positions);
    }
}
