package com.finex.loadgenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Configuration for the load generator.
 */
public record LoadConfig(
        List<String> symbols,
        List<Long> accounts,
        int ordersPerSymbol,
        BigDecimal basePrice,
        BigDecimal quantity,
        BigDecimal priceJitter,
        long seed,
        Instant startTime) {

    public LoadConfig {
        if (symbols == null || symbols.isEmpty()) {
            throw new IllegalArgumentException("symbols must not be empty");
        }
        if (accounts == null || accounts.isEmpty()) {
            throw new IllegalArgumentException("accounts must not be empty");
        }
        if (ordersPerSymbol <= 0) {
            throw new IllegalArgumentException("ordersPerSymbol must be positive");
        }
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("basePrice must be positive");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (priceJitter == null || priceJitter.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("priceJitter must not be negative");
        }
        if (startTime == null) {
            throw new IllegalArgumentException("startTime must not be null");
        }
        symbols = List.copyOf(symbols);
        accounts = List.copyOf(accounts);
    }

    public static LoadConfig defaults() {
        return new LoadConfig(
                List.of("BTC-USD"),
                List.of(100L, 200L),
                100,
                new BigDecimal("50000"),
                new BigDecimal("0.01"),
                new BigDecimal("100"),
                12345L,
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
