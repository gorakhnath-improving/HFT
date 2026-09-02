package com.finex.marketdata;

import java.time.Instant;
import java.util.List;

/**
 * Full order-book snapshot published whenever the book changes.
 */
public record BookUpdate(
        String symbol,
        List<PriceLevel> bids,
        List<PriceLevel> asks,
        Instant timestamp) implements MarketDataEvent {

    public BookUpdate {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        bids = List.copyOf(bids);
        asks = List.copyOf(asks);
    }
}
