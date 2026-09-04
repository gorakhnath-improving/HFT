package com.finex.marketdata;

import java.math.BigDecimal;

/**
 * A single price level in a market data book update.
 */
public record PriceLevel(BigDecimal price, BigDecimal quantity) {

    public PriceLevel {
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }
}
