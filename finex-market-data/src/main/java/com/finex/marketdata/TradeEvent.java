package com.finex.marketdata;

import java.time.Instant;

import com.finex.common.domain.Trade;

/**
 * Published for every trade that occurs in the matching engine.
 */
public record TradeEvent(String symbol, Trade trade, Instant timestamp) implements MarketDataEvent {

    public TradeEvent {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}
