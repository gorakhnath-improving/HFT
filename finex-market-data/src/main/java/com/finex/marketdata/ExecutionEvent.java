package com.finex.marketdata;

import java.time.Instant;

import com.finex.common.domain.Trade;
import com.finex.common.domain.enums.OrderStatus;

/**
 * Published for each side of a trade, indicating how an individual order was filled.
 */
public record ExecutionEvent(
        long orderId,
        long accountId,
        String symbol,
        Trade trade,
        OrderStatus resultingStatus,
        Instant timestamp) implements MarketDataEvent {

    public ExecutionEvent {
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (trade == null) {
            throw new IllegalArgumentException("trade must not be null");
        }
        if (resultingStatus == null) {
            throw new IllegalArgumentException("resultingStatus must not be null");
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
    }
}
