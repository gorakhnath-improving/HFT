package com.finex.eventlog;

import java.math.BigDecimal;

import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

/**
 * Immutable command to submit a new order. Used as the event-log payload for replay.
 */
public record SubmitOrderCommand(
        long accountId,
        String clientOrderId,
        String symbol,
        Side side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity) {

    public SubmitOrderCommand {
        if (clientOrderId == null || clientOrderId.isBlank()) {
            throw new IllegalArgumentException("clientOrderId must not be blank");
        }
        if (symbol == null || symbol.isBlank()) {
            throw new IllegalArgumentException("symbol must not be blank");
        }
        if (side == null) {
            throw new IllegalArgumentException("side must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (type == OrderType.LIMIT && (price == null || price.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new IllegalArgumentException("LIMIT orders require a positive price");
        }
    }
}
