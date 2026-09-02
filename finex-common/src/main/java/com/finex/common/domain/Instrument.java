package com.finex.common.domain;

import java.math.BigDecimal;

import com.finex.common.domain.enums.InstrumentStatus;

/**
 * A tradable instrument (equity or crypto pair).
 *
 * @param symbol      trading symbol, e.g. "BTC-USD" or "AAPL"
 * @param baseAsset   asset being bought/sold, e.g. "BTC"
 * @param quoteAsset  asset used for pricing, e.g. "USD"
 * @param tickSize    minimum price increment, must be positive
 * @param lotSize     minimum quantity increment, must be positive
 * @param status      trading status of the instrument
 */
public record Instrument(
        String symbol,
        String baseAsset,
        String quoteAsset,
        BigDecimal tickSize,
        BigDecimal lotSize,
        InstrumentStatus status) {

    public Instrument {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (baseAsset == null || baseAsset.isBlank()) {
            throw new IllegalArgumentException("baseAsset must not be blank");
        }
        if (quoteAsset == null || quoteAsset.isBlank()) {
            throw new IllegalArgumentException("quoteAsset must not be blank");
        }
        if (tickSize == null || tickSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("tickSize must be positive");
        }
        if (lotSize == null || lotSize.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("lotSize must be positive");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }
}
