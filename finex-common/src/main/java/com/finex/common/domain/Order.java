package com.finex.common.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.finex.common.domain.enums.OrderStatus;
import com.finex.common.domain.enums.OrderType;
import com.finex.common.domain.enums.Side;

/**
 * A resting or active order in the FinEx domain.
 * Records are immutable; state changes (fills, cancels) are represented by creating new
 * instances. This keeps the baseline deterministic and easy to test (Master Plan §50).
 *
 * @param orderId          exchange-assigned unique order id
 * @param clientOrderId    client-provided idempotency key
 * @param accountId        originating account
 * @param symbol           instrument symbol
 * @param side             BUY or SELL
 * @param type             LIMIT or MARKET
 * @param price            limit price; null for MARKET orders
 * @param quantity         total quantity ordered; must be positive
 * @param remainingQuantity quantity not yet filled; initially equals quantity
 * @param sequence         global ordering sequence number
 * @param timestamp        order submission time
 * @param status           current lifecycle status
 */
public record Order(
        long orderId,
        String clientOrderId,
        long accountId,
        String symbol,
        Side side,
        OrderType type,
        BigDecimal price,
        BigDecimal quantity,
        BigDecimal remainingQuantity,
        long sequence,
        Instant timestamp,
        OrderStatus status) {

    public Order {
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
        if (remainingQuantity == null ||
                remainingQuantity.compareTo(BigDecimal.ZERO) < 0 ||
                remainingQuantity.compareTo(quantity) > 0) {
            throw new IllegalArgumentException("remainingQuantity must be between zero and quantity");
        }
        if (type == OrderType.LIMIT) {
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("LIMIT orders require a positive price");
            }
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("timestamp must not be null");
        }
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
    }

    /**
     * Returns a copy of this order with a reduced remaining quantity and updated status.
     * Useful when the matching engine reports a partial fill.
     *
     * @param filled     additional quantity filled in this event
     * @param newStatus  resulting order status
     * @param newInstant timestamp of the fill event
     */
    public Order withFill(BigDecimal filled, OrderStatus newStatus, Instant newInstant) {
        if (filled == null || filled.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("filled quantity must be positive");
        }
        BigDecimal newRemaining = remainingQuantity.subtract(filled);
        return new Order(
                orderId,
                clientOrderId,
                accountId,
                symbol,
                side,
                type,
                price,
                quantity,
                newRemaining,
                sequence,
                newInstant,
                newStatus);
    }

    /**
     * Returns a copy of this order in a cancelled state.
     */
    public Order cancelled(Instant cancelledAt) {
        return new Order(
                orderId,
                clientOrderId,
                accountId,
                symbol,
                side,
                type,
                price,
                quantity,
                remainingQuantity,
                sequence,
                cancelledAt,
                OrderStatus.CANCELLED);
    }

    /**
     * Returns a copy of this order in a rejected state with no remaining quantity.
     */
    public Order rejected(Instant rejectedAt) {
        return new Order(
                orderId,
                clientOrderId,
                accountId,
                symbol,
                side,
                type,
                price,
                quantity,
                BigDecimal.ZERO,
                sequence,
                rejectedAt,
                OrderStatus.REJECTED);
    }
}
